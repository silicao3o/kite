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

    @Value("${docker.monitor.log-tail-lines:50}")
    private int logTailLines;

    public DockerService(DockerClient dockerClient, NodeRegistry nodeRegistry, NodeDockerClientFactory nodeClientFactory) {
        this.dockerClient = dockerClient;
        this.nodeRegistry = nodeRegistry;
        this.nodeClientFactory = nodeClientFactory;
    }

    public ContainerDeathEvent buildDeathEvent(String containerId, String action) {
        try {
            InspectContainerResponse inspection = dockerClient.inspectContainerCmd(containerId).exec();
            InspectContainerResponse.ContainerState state = inspection.getState();

            String containerName = DockerContainerNames.stripLeadingSlash(inspection.getName());

            Long exitCode = state.getExitCodeLong();
            Boolean oomKilled = state.getOOMKilled();
            String finishedAt = state.getFinishedAt();

            LocalDateTime deathTime = parseDockerTime(finishedAt);
            String lastLogs = getContainerLogs(containerId);

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
                    .build();

        } catch (Exception e) {
            log.error("컨테이너 정보 조회 실패: {}", containerId, e);
            return ContainerDeathEvent.builder()
                    .containerId(containerId)
                    .containerName("Unknown")
                    .deathTime(LocalDateTime.now())
                    .action(action)
                    .lastLogs("로그 조회 실패: " + e.getMessage())
                    .build();
        }
    }

    public String getContainerLogs(String containerId) {
        // 로컬에서 먼저 조회
        try {
            return fetchLogs(dockerClient, containerId);
        } catch (NotFoundException ignored) {
            // 로컬에 없으면 노드 탐색
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "로그 조회 중단";
        } catch (Exception e) {
            log.error("로그 조회 실패 (local): {}", containerId, e);
        }

        // 등록된 노드에서 탐색
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

        log.debug("로그 조회 스킵 — 컨테이너 없음: {}", containerId);
        return "";
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
                DockerClient client = nodeClientFactory.createClient(node);
                client.listContainersCmd().withShowAll(showAll).exec()
                        .stream()
                        .map(c -> toContainerInfo(c, node.getId(), node.getName()))
                        .forEach(result::add);
            } catch (Exception e) {
                log.warn("[멀티노드] {} ({}:{}) 컨테이너 조회 실패: {}",
                        node.getName(), node.getHost(), node.getPort(), e.getMessage());
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

    public boolean restartContainer(String containerId) {
        return restartContainer(containerId, dockerClient);
    }

    public boolean restartContainer(String containerId, DockerClient client) {
        try {
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
}
