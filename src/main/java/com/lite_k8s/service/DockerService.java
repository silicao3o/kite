package com.lite_k8s.service;

import com.lite_k8s.model.ContainerDeathEvent;
import com.lite_k8s.model.ContainerInfo;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import com.github.dockerjava.api.exception.NotFoundException;
import com.lite_k8s.util.DockerContainerNames;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DockerService {

    private final DockerClient dockerClient;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;
    private final OwnActionTracker ownActionTracker;

    @Value("${docker.monitor.log-tail-lines:50}")
    private int logTailLines;

    public DockerService(DockerClient dockerClient, NodeRegistry nodeRegistry, NodeDockerClientFactory nodeClientFactory) {
        this(dockerClient, nodeRegistry, nodeClientFactory, new OwnActionTracker());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DockerService(DockerClient dockerClient, NodeRegistry nodeRegistry,
                         NodeDockerClientFactory nodeClientFactory, OwnActionTracker ownActionTracker) {
        this.dockerClient = dockerClient;
        this.nodeRegistry = nodeRegistry;
        this.nodeClientFactory = nodeClientFactory;
        this.ownActionTracker = ownActionTracker;
    }

    public ContainerDeathEvent buildDeathEvent(String containerId, String action) {
        return buildDeathEvent(containerId, action, null);
    }

    public ContainerDeathEvent buildDeathEvent(String containerId, String action, String nodeId) {
        DockerClient client = resolveClient(nodeId);
        return buildDeathEventWithClient(containerId, action, client, nodeId);
    }

    private DockerClient resolveClient(String nodeId) {
        if (nodeId == null || nodeRegistry == null) {
            return dockerClient;
        }
        return nodeRegistry.findById(nodeId)
                .map(nodeClientFactory::createClient)
                .orElse(dockerClient);
    }

    // nodeId로 사람이 읽을 수 있는 노드 이름을 조회 — 이메일 알림 등에서 표시용
    // nodeId가 null이거나 NodeRegistry가 없으면 "local" 반환 (로컬 단일 모드)
    private String resolveNodeName(String nodeId) {
        if (nodeId == null || nodeRegistry == null) {
            return "local";
        }
        return nodeRegistry.findById(nodeId)
                .map(Node::getName)
                .orElse("unknown");
    }

    private ContainerDeathEvent buildDeathEventWithClient(String containerId, String action, DockerClient client, String nodeId) {
        // nodeId → 사람이 읽을 수 있는 노드 이름 변환
        String nodeName = resolveNodeName(nodeId);

        try {
            InspectContainerResponse inspection = client.inspectContainerCmd(containerId).exec();
            InspectContainerResponse.ContainerState state = inspection.getState();

            String containerName = DockerContainerNames.stripLeadingSlash(inspection.getName());

            Long exitCode = state.getExitCodeLong();
            Boolean oomKilled = state.getOOMKilled();
            String finishedAt = state.getFinishedAt();

            LocalDateTime deathTime = parseDockerTime(finishedAt);
            String lastLogs = getContainerLogs(containerId, null, null); // 내부 호출 — name 없이 ID로 조회

            return ContainerDeathEvent.builder()
                    .containerId(containerId)
                    .containerName(containerName)
                    .imageName(inspection.getConfig().getImage())
                    .deathTime(deathTime)
                    .exitCode(exitCode)
                    .oomKilled(oomKilled != null && oomKilled)
                    .action(action)
                    .lastLogs(lastLogs)
                    .labels(inspection.getConfig().getLabels())
                    .nodeId(nodeId)        // 노드 식별자
                    .nodeName(nodeName)    // 사람이 읽을 수 있는 노드 이름 (이메일 알림용)
                    .build();

        } catch (Exception e) {
            log.error("컨테이너 정보 조회 실패: {}", containerId, e);
            return ContainerDeathEvent.builder()
                    .containerId(containerId)
                    .containerName("Unknown")
                    .deathTime(LocalDateTime.now())
                    .action(action)
                    .lastLogs("로그 조회 실패: " + e.getMessage())
                    .nodeId(nodeId)        // 예외 시에도 노드 정보는 유지
                    .nodeName(nodeName)
                    .build();
        }
    }

    // 컨테이너 로그 조회 — ID로 먼저 찾고, 실패 시 이름으로 재검색 (재기동 대응)
    // containerId: 프론트가 가지고 있는 컨테이너 ID (재기동 시 이전 ID일 수 있음)
    // containerName: 프론트에서 보내는 컨테이너 이름 (예: "dev/chat-quvi-test") — 3단계 fallback용
    // nodeId: 컨테이너가 속한 노드 ID — 3단계에서 해당 노드에서만 검색하여 오매칭 방지
    public String getContainerLogs(String containerId, String containerName, String nodeId) {
        // 1단계: 로컬에서 ID로 조회
        try {
            return fetchLogs(dockerClient, containerId);
        } catch (NotFoundException ignored) {
            // 로컬에 없으면 다음 단계로
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "로그 조회 중단";
        } catch (Exception e) {
            log.error("로그 조회 실패 (local): {}", containerId, e);
        }

        // 2단계: 등록된 노드에서 ID로 조회
        if (nodeRegistry != null) {
            for (Node node : nodeRegistry.findAll()) {
                try {
                    DockerClient client = nodeClientFactory.createClient(node);
                    return fetchLogs(client, containerId);
                } catch (NotFoundException ignored) {
                    // 해당 노드에 없음, 다음 노드 시도
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "로그 조회 중단";
                } catch (Exception e) {
                    log.warn("[멀티노드] {} 로그 조회 실패: {}", node.getName(), e.getMessage());
                }
            }
        }

        // 3단계: ID로 못 찾았고 이름이 있으면 — 이름으로 재검색 (컨테이너 재기동 대응)
        // nodeId가 있으면 해당 노드에서만 검색하여 동일 이름 컨테이너 오매칭 방지
        if (containerName != null && !containerName.isEmpty()) {
            if (nodeId != null && !nodeId.isEmpty() && nodeRegistry != null) {
                // nodeId가 있으면 해당 노드에서만 검색 — res/quvi 재기동 시 dev/quvi를 잘못 찾는 오매칭 방지
                Node targetNode = nodeRegistry.findById(nodeId).orElse(null);
                if (targetNode != null) {
                    try {
                        DockerClient client = nodeClientFactory.createClient(targetNode);
                        String newId = findContainerIdByName(client, containerName); // 해당 노드의 컨테이너 목록에서 이름으로 새 ID 검색
                        if (newId != null) {
                            return fetchLogs(client, newId); // 새 ID로 로그 조회 — 재기동 후에도 즉시 로그 반환
                        }
                    } catch (Exception e) {
                        log.warn("[멀티노드] {} 이름 기반 로그 조회 실패: {}", targetNode.getName(), e.getMessage());
                    }
                }
            } else {
                // nodeId 없으면 기존 로직: 로컬 → 전체 노드 순회
                String newId = findContainerIdByName(dockerClient, containerName);
                if (newId != null) {
                    try {
                        return fetchLogs(dockerClient, newId);
                    } catch (Exception e) {
                        log.error("이름 기반 로그 조회 실패 (local): {} ({})", containerName, newId, e);
                    }
                }
                if (nodeRegistry != null) {
                    for (Node node : nodeRegistry.findAll()) {
                        try {
                            DockerClient client = nodeClientFactory.createClient(node);
                            newId = findContainerIdByName(client, containerName);
                            if (newId != null) {
                                return fetchLogs(client, newId);
                            }
                        } catch (Exception e) {
                            log.warn("[멀티노드] {} 이름 기반 로그 조회 실패: {}", node.getName(), e.getMessage());
                        }
                    }
                }
            }
        }

        log.debug("로그 조회 스킵 — 컨테이너 없음: {}", containerId);
        return "";
    }

    // 컨테이너 이름으로 현재 ID를 찾는 메서드 — Docker는 이름 앞에 "/"를 붙여서 저장
    // 프론트에서 "dev/chat-quvi-test" 형태로 오면 노드 접두사를 제거하고 "chat-quvi-test"로 검색
    private String findContainerIdByName(DockerClient client, String name) {
        try {
            // 노드 접두사 제거 (dev/chat-quvi-test → chat-quvi-test)
            String searchName = name.contains("/") ? name.substring(name.lastIndexOf("/") + 1) : name;
            return client.listContainersCmd().withShowAll(true).exec()
                    .stream()
                    .filter(c -> c.getNames() != null &&
                            java.util.Arrays.stream(c.getNames())
                                    .anyMatch(n -> n.equals("/" + searchName) || n.endsWith("/" + searchName)))
                    .map(c -> c.getId())
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("이름으로 컨테이너 검색 실패: {}", name, e);
            return null;
        }
    }

    private String fetchLogs(DockerClient client, String containerId) throws InterruptedException {
        List<String> logs = new ArrayList<>();

        LogContainerResultCallback callback = new LogContainerResultCallback() {
            @Override
            public void onNext(Frame frame) {
                logs.add(new String(frame.getPayload()).trim());
            }
        };

        client.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withTail(logTailLines)
                .withTimestamps(true)
                .exec(callback)
                .awaitCompletion(10, TimeUnit.SECONDS);

        return String.join("\n", logs);
    }

    private LocalDateTime parseDockerTime(String dockerTime) {
        if (dockerTime == null || dockerTime.isEmpty() || dockerTime.equals("0001-01-01T00:00:00Z")) {
            return LocalDateTime.now();
        }
        try {
            Instant instant = Instant.parse(dockerTime);
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    public List<ContainerInfo> listContainers(boolean showAll) {
        List<ContainerInfo> result = new ArrayList<>();

        // 로컬 컨테이너
        dockerClient.listContainersCmd().withShowAll(showAll).exec()
                .stream()
                .map(c -> toContainerInfo(c, null, "local"))
                .forEach(result::add);

        // 등록된 노드 컨테이너
        if (nodeRegistry == null) return result;
        for (Node node : nodeRegistry.findAll()) {
            try {
                log.info("[멀티노드] {} 컨테이너 조회 시작 ({}:{})", node.getName(), node.getHost(), node.getPort());
                DockerClient client = nodeClientFactory.createClient(node);
                List<ContainerInfo> nodeContainers = client.listContainersCmd().withShowAll(showAll).exec()
                        .stream()
                        .map(c -> toContainerInfo(c, node.getId(), node.getName()))
                        .toList();
                log.info("[멀티노드] {} 컨테이너 {}개 조회 성공", node.getName(), nodeContainers.size());
                result.addAll(nodeContainers);
            } catch (Exception e) {
                log.warn("[멀티노드] {} ({}:{}) 컨테이너 조회 실패: {}",
                        node.getName(), node.getHost(), node.getPort(), e.getMessage(), e);
            }
        }

        return result;
    }

    public ContainerInfo getContainer(String containerId) {
        // 로컬에서 먼저 조회
        try {
            InspectContainerResponse inspection = dockerClient.inspectContainerCmd(containerId).exec();
            return toContainerInfo(inspection);
        } catch (NotFoundException ignored) {
            // 로컬에 없으면 등록된 노드에서 탐색
        } catch (Exception e) {
            log.error("컨테이너 조회 실패 (local): {}", containerId, e);
        }

        // 등록된 노드에서 탐색
        if (nodeRegistry != null) {
            for (Node node : nodeRegistry.findAll()) {
                try {
                    DockerClient client = nodeClientFactory.createClient(node);
                    InspectContainerResponse inspection = client.inspectContainerCmd(containerId).exec();
                    return toContainerInfo(inspection);
                } catch (NotFoundException ignored) {
                    // 해당 노드에 없음, 다음 노드 시도
                } catch (Exception e) {
                    log.warn("[멀티노드] {} 컨테이너 상세 조회 실패: {}", node.getName(), e.getMessage());
                }
            }
        }

        log.debug("컨테이너 없음 (전체 노드 탐색): {}", containerId);
        return null;
    }

    private ContainerInfo toContainerInfo(Container container, String nodeId, String nodeName) {
        String name = DockerContainerNames.extractName(container, "unknown");

        List<ContainerInfo.PortMapping> ports = new ArrayList<>();
        if (container.getPorts() != null) {
            for (ContainerPort port : container.getPorts()) {
                ports.add(ContainerInfo.PortMapping.builder()
                        .privatePort(port.getPrivatePort() != null ? port.getPrivatePort() : 0)
                        .publicPort(port.getPublicPort() != null ? port.getPublicPort() : 0)
                        .type(port.getType() != null ? port.getType() : "tcp")
                        .build());
            }
        }

        return ContainerInfo.builder()
                .id(container.getId())
                .shortId(container.getId().substring(0, Math.min(12, container.getId().length())))
                .name(name)
                .image(container.getImage())
                .status(container.getStatus())
                .state(container.getState())
                .created(LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(container.getCreated()),
                        ZoneId.systemDefault()))
                .ports(ports)
                .labels(container.getLabels())
                .nodeId(nodeId)
                .nodeName(nodeName)
                .build();
    }

    public boolean stopContainer(String containerId) {
        return stopContainer(containerId, dockerClient);
    }

    public boolean stopContainer(String containerId, String nodeId) {
        if (nodeId == null) {
            return stopContainer(containerId, dockerClient);
        }
        return nodeRegistry.findById(nodeId)
                .map(node -> stopContainer(containerId, nodeClientFactory.createClient(node)))
                .orElseGet(() -> {
                    log.warn("노드를 찾을 수 없어 로컬 클라이언트로 정지 시도: nodeId={}, containerId={}", nodeId, containerId);
                    return stopContainer(containerId, dockerClient);
                });
    }

    public boolean stopContainer(String containerId, DockerClient client) {
        try {
            ownActionTracker.markOwnAction(containerId);
            client.stopContainerCmd(containerId).exec();
            log.info("컨테이너 강제 정지 성공: {}", containerId);
            return true;
        } catch (Exception e) {
            log.error("컨테이너 강제 정지 실패: {}", containerId, e);
            return false;
        }
    }

    public boolean restartContainer(String containerId) {
        return restartContainer(containerId, dockerClient);
    }

    public boolean restartContainer(String containerId, String nodeId) {
        if (nodeId == null) {
            return restartContainer(containerId, dockerClient);
        }
        return nodeRegistry.findById(nodeId)
                .map(node -> restartContainer(containerId, nodeClientFactory.createClient(node)))
                .orElseGet(() -> {
                    log.warn("노드를 찾을 수 없어 로컬 클라이언트로 재시작 시도: nodeId={}, containerId={}", nodeId, containerId);
                    return restartContainer(containerId, dockerClient);
                });
    }

    public boolean restartContainer(String containerId, DockerClient client) {
        try {
            ownActionTracker.markOwnAction(containerId);
            client.startContainerCmd(containerId).exec();
            log.info("컨테이너 재시작 성공: {}", containerId);
            return true;
        } catch (com.github.dockerjava.api.exception.NotModifiedException e) {
            log.info("컨테이너 이미 실행 중 (재시작 불필요): {}", containerId);
            return true;
        } catch (Exception e) {
            log.error("컨테이너 재시작 실패: {}", containerId, e);
            return false;
        }
    }

    private ContainerInfo toContainerInfo(InspectContainerResponse inspection) {
        String name = DockerContainerNames.stripLeadingSlash(inspection.getName());

        InspectContainerResponse.ContainerState state = inspection.getState();

        return ContainerInfo.builder()
                .id(inspection.getId())
                .shortId(inspection.getId().substring(0, Math.min(12, inspection.getId().length())))
                .name(name)
                .image(inspection.getConfig().getImage())
                .status(state.getStatus())
                .state(state.getStatus())
                .created(parseDockerTime(inspection.getCreated()))
                .ports(new ArrayList<>())
                .labels(inspection.getConfig().getLabels())
                .build();
    }

    public List<String> getContainerEnvVars(String containerId, String nodeId) {
        DockerClient client = resolveClient(nodeId);
        InspectContainerResponse inspection = client.inspectContainerCmd(containerId).exec();
        String[] env = inspection.getConfig().getEnv();
        return env != null ? List.of(env) : List.of();
    }
}
