package com.lite_k8s.compose;

import com.lite_k8s.envprofile.EnvProfileResolver;
import com.github.dockerjava.api.DockerClient;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

@Slf4j
@RestController
@RequestMapping("/api/service-definitions")
@RequiredArgsConstructor
public class ServiceDefinitionController {

    private final ServiceDefinitionRepository repository;
    private final ServiceDeployer deployer;
    private final DockerClient dockerClient;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String name = asString(body.get("name"));
        String composeYaml = asString(body.get("composeYaml"));
        if (name == null || name.isBlank()) return ResponseEntity.badRequest().body("name은 필수입니다");
        if (composeYaml == null || composeYaml.isBlank()) return ResponseEntity.badRequest().body("composeYaml은 필수입니다");

        ServiceDefinition def = ServiceDefinition.builder()
                .name(name)
                .composeYaml(composeYaml)
                .nodeEnvMappings(buildNodeEnvMappings(body))
                .build();

        return ResponseEntity.status(201).body(repository.save(def));
    }

    @GetMapping
    public List<ServiceDefinition> list() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Optional<ServiceDefinition> maybe = repository.findById(id);
        if (maybe.isEmpty()) return ResponseEntity.notFound().build();

        ServiceDefinition def = maybe.get();
        if (body.containsKey("name")) def.setName(asString(body.get("name")));
        if (body.containsKey("composeYaml")) def.setComposeYaml(asString(body.get("composeYaml")));
        if (body.containsKey("nodeEnvMappings") || body.containsKey("envProfileId") || body.containsKey("nodeNames")) {
            def.setNodeEnvMappings(buildNodeEnvMappings(body));
        }
        if (body.containsKey("status")) {
            try { def.setStatus(ServiceDefinition.Status.valueOf(asString(body.get("status")))); }
            catch (IllegalArgumentException ignored) {}
        }

        return ResponseEntity.ok(repository.save(def));
    }

    /** 서비스 배포 — compose 파싱 → Docker API로 컨테이너 생성 */
    @PostMapping("/{id}/deploy")
    public ResponseEntity<?> deploy(@PathVariable String id,
                                     @RequestParam(required = false) List<String> activeProfiles) {
        Optional<ServiceDefinition> maybe = repository.findById(id);
        if (maybe.isEmpty()) return ResponseEntity.notFound().build();

        ServiceDefinition def = maybe.get();
        long startMs = System.currentTimeMillis();
        try {
            List<ParsedService> services = ComposeParser.parse(def.getComposeYaml(), activeProfiles);
            log.info("[deploy] 시작: defId={}, name={}, services={}, profiles={}",
                    id, def.getName(),
                    services.stream().map(ParsedService::getServiceName).toList(),
                    activeProfiles);

            List<String> containerIds = new CopyOnWriteArrayList<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            Map<String, String> mappings = def.getNodeEnvMappings();
            if (mappings == null || mappings.isEmpty()) {
                log.info("[deploy] 노드 매핑 없음 → 로컬에 {}개 서비스 병렬 배포", services.size());
                for (ParsedService svc : services) {
                    futures.add(CompletableFuture.runAsync(() -> {
                        log.info("[deploy] 서비스 시작: name={}, image={}, container={}",
                                svc.getServiceName(), svc.getImage(), svc.getContainerName());
                        containerIds.add(deployer.deployWithDefinitionId(svc, null, null, def.getId()));
                    }));
                }
            } else {
                log.info("[deploy] 노드별 매핑: {}", mappings.keySet());
                for (Map.Entry<String, String> entry : mappings.entrySet()) {
                    String nodeName = entry.getKey();
                    String profileId = entry.getValue();
                    String nodeId = nodeName.isEmpty() ? null : resolveNodeId(nodeName);
                    log.info("[deploy] 노드 {} (profileId={}, nodeId={}) 에 {}개 서비스 병렬 배포",
                            nodeName, profileId, nodeId, services.size());
                    for (ParsedService svc : services) {
                        futures.add(CompletableFuture.runAsync(() -> {
                            log.info("[deploy] 서비스 시작: node={}, name={}, image={}, container={}",
                                    nodeName, svc.getServiceName(), svc.getImage(), svc.getContainerName());
                            containerIds.add(deployer.deployWithDefinitionId(svc, profileId, nodeId, def.getId()));
                        }));
                    }
                }
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            def.setStatus(ServiceDefinition.Status.DEPLOYED);
            repository.save(def);

            long duration = System.currentTimeMillis() - startMs;
            log.info("[deploy] 완료: defId={}, name={}, deployed={}개, duration={}ms",
                    id, def.getName(), containerIds.size(), duration);
            return ResponseEntity.ok(Map.of("status", "deployed", "containerIds", containerIds));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startMs;
            log.error("[deploy] 실패: defId={}, name={}, duration={}ms — {}",
                    id, def.getName(), duration, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("배포 실패: " + e.getMessage());
        }
    }

    /** 서비스 중지 — 해당 정의로 생성된 컨테이너 stop + remove */
    @PostMapping("/{id}/stop")
    public ResponseEntity<?> stop(@PathVariable String id) {
        Optional<ServiceDefinition> maybe = repository.findById(id);
        if (maybe.isEmpty()) return ResponseEntity.notFound().build();

        ServiceDefinition def = maybe.get();
        long startMs = System.currentTimeMillis();
        try {
            int totalRemoved = 0;
            int totalFailed = 0;

            // 각 노드별로 컨테이너 정리
            Set<String> nodeIds = new LinkedHashSet<>();
            Map<String, String> mappings = def.getNodeEnvMappings();
            if (mappings == null || mappings.isEmpty()) {
                nodeIds.add(null); // 로컬
            } else {
                for (String nodeName : mappings.keySet()) {
                    nodeIds.add(nodeName.isEmpty() ? null : resolveNodeId(nodeName));
                }
            }
            log.info("[serviceStop] 시작: defId={}, name={}, 대상 노드={}",
                    id, def.getName(), nodeIds);

            for (String nodeId : nodeIds) {
                String nodeName = resolveNodeName(nodeId);
                DockerClient client = resolveClient(nodeId);
                var containers = client.listContainersCmd()
                        .withShowAll(true)
                        .withLabelFilter(Map.of("kite.service-definition-id", id))
                        .exec();
                log.info("[serviceStop] 노드 {} — 정리 대상 {}개", nodeName, containers.size());

                for (var container : containers) {
                    String cName = container.getNames() != null && container.getNames().length > 0
                            ? container.getNames()[0].replaceFirst("^/", "") : container.getId();
                    try {
                        if (container.getState() != null && container.getState().contains("running")) {
                            log.info("[serviceStop] stop 호출: node={}, container={} ({})",
                                    nodeName, cName, container.getId());
                            client.stopContainerCmd(container.getId()).exec();
                        } else {
                            log.info("[serviceStop] 이미 중지됨, stop 스킵: node={}, container={} (state={})",
                                    nodeName, cName, container.getState());
                        }
                        log.info("[serviceStop] remove 호출: node={}, container={} ({})",
                                nodeName, cName, container.getId());
                        client.removeContainerCmd(container.getId()).exec();
                        totalRemoved++;
                    } catch (Exception e) {
                        totalFailed++;
                        log.warn("[serviceStop] 컨테이너 정리 실패: node={}, container={} ({}) — {}",
                                nodeName, cName, container.getId(), e.getMessage(), e);
                    }
                }
            }

            def.setStatus(ServiceDefinition.Status.STOPPED);
            repository.save(def);

            long duration = System.currentTimeMillis() - startMs;
            log.info("[serviceStop] 완료: defId={}, name={}, removed={}, failed={}, duration={}ms",
                    id, def.getName(), totalRemoved, totalFailed, duration);
            return ResponseEntity.ok(Map.of("status", "stopped", "removed", totalRemoved));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startMs;
            log.error("[serviceStop] 실패: defId={}, name={}, duration={}ms — {}",
                    id, def.getName(), duration, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("중지 실패: " + e.getMessage());
        }
    }

    /** Compose YAML에 정의된 profiles 목록 조회 */
    @GetMapping("/{id}/profiles")
    public ResponseEntity<?> getProfiles(@PathVariable String id) {
        Optional<ServiceDefinition> maybe = repository.findById(id);
        if (maybe.isEmpty()) return ResponseEntity.notFound().build();

        List<ParsedService> all = ComposeParser.parse(maybe.get().getComposeYaml());
        List<String> profiles = all.stream()
                .flatMap(s -> s.getProfiles() != null ? s.getProfiles().stream() : Stream.empty())
                .distinct()
                .sorted()
                .toList();
        return ResponseEntity.ok(profiles);
    }

    /** 73. 서비스별 노드/컨테이너 목록 조회 */
    @GetMapping("/{id}/containers")
    public ResponseEntity<?> listContainers(@PathVariable String id) {
        Optional<ServiceDefinition> maybe = repository.findById(id);
        if (maybe.isEmpty()) return ResponseEntity.notFound().build();

        ServiceDefinition def = maybe.get();
        List<Map<String, Object>> result = new ArrayList<>();

        Set<String> nodeIds = resolveNodeIds(def);
        for (String nodeId : nodeIds) {
            DockerClient client = resolveClient(nodeId);
            String nodeName = nodeId == null ? "local" : resolveNodeName(nodeId);
            try {
                var containers = client.listContainersCmd()
                        .withShowAll(true)
                        .withLabelFilter(Map.of("kite.service-definition-id", id))
                        .exec();
                for (var c : containers) {
                    String name = c.getNames() != null && c.getNames().length > 0
                            ? c.getNames()[0].replaceFirst("^/", "") : c.getId();
                    result.add(Map.of(
                            "containerId", c.getId(),
                            "name", name,
                            "state", c.getState() != null ? c.getState() : "unknown",
                            "image", c.getImage() != null ? c.getImage() : "",
                            "nodeName", nodeName,
                            "nodeId", nodeId != null ? nodeId : ""
                    ));
                }
            } catch (Exception e) {
                log.warn("컨테이너 조회 실패 (node={}): {}", nodeName, e.getMessage());
            }
        }

        return ResponseEntity.ok(result);
    }

    /** 74. 노드별 컨테이너 stop */
    @PostMapping("/{id}/nodes/{nodeName}/stop")
    public ResponseEntity<?> stopByNode(@PathVariable String id, @PathVariable String nodeName) {
        return executeOnNode(id, nodeName, "stop");
    }

    /** 75. 노드별 컨테이너 restart */
    @PostMapping("/{id}/nodes/{nodeName}/restart")
    public ResponseEntity<?> restartByNode(@PathVariable String id, @PathVariable String nodeName) {
        return executeOnNode(id, nodeName, "restart");
    }

    /** 76. 개별 컨테이너 stop */
    @PostMapping("/{id}/containers/{containerId}/stop")
    public ResponseEntity<?> stopContainer(@PathVariable String id, @PathVariable String containerId) {
        return executeOnContainer(id, containerId, "stop");
    }

    /** 76. 개별 컨테이너 restart */
    @PostMapping("/{id}/containers/{containerId}/restart")
    public ResponseEntity<?> restartContainer(@PathVariable String id, @PathVariable String containerId) {
        return executeOnContainer(id, containerId, "restart");
    }

    /** 노드별 재배포 — stop+remove 후 최신 설정으로 재생성 */
    @PostMapping("/{id}/nodes/{nodeName}/redeploy")
    public ResponseEntity<?> redeployByNode(@PathVariable String id, @PathVariable String nodeName,
                                             @RequestParam(required = false) List<String> activeProfiles) {
        Optional<ServiceDefinition> maybe = repository.findById(id);
        if (maybe.isEmpty()) return ResponseEntity.notFound().build();

        ServiceDefinition def = maybe.get();
        String nodeId = "local".equals(nodeName) ? null : resolveNodeId(nodeName);
        DockerClient client = resolveClient(nodeId);
        long startMs = System.currentTimeMillis();
        log.info("[allRedeploy] 시작: defId={}, name={}, node={}, profiles={}",
                id, def.getName(), nodeName, activeProfiles);

        try {
            // 1. 기존 컨테이너 stop + remove
            var containers = client.listContainersCmd()
                    .withShowAll(true)
                    .withLabelFilter(Map.of("kite.service-definition-id", id))
                    .exec();
            log.info("[allRedeploy] 정리 대상 {}개 (node={})", containers.size(), nodeName);

            int cleaned = 0, cleanFailed = 0;
            for (var c : containers) {
                String cName = c.getNames() != null && c.getNames().length > 0
                        ? c.getNames()[0].replaceFirst("^/", "") : c.getId();
                try {
                    if ("running".equals(c.getState())) {
                        log.info("[allRedeploy] stop: node={}, container={} ({})", nodeName, cName, c.getId());
                        client.stopContainerCmd(c.getId()).exec();
                    } else {
                        log.info("[allRedeploy] 이미 중지됨, stop 스킵: node={}, container={} (state={})",
                                nodeName, cName, c.getState());
                    }
                    log.info("[allRedeploy] remove: node={}, container={} ({})", nodeName, cName, c.getId());
                    client.removeContainerCmd(c.getId()).exec();
                    cleaned++;
                } catch (Exception e) {
                    cleanFailed++;
                    log.warn("[allRedeploy] 컨테이너 정리 실패: node={}, container={} — {}",
                            nodeName, cName, e.getMessage(), e);
                }
            }
            log.info("[allRedeploy] 정리 완료: cleaned={}, failed={}", cleaned, cleanFailed);

            // 2. 최신 설정으로 재배포
            List<ParsedService> services = ComposeParser.parse(def.getComposeYaml(), activeProfiles);
            String profileId = def.getNodeEnvMappings() != null ? def.getNodeEnvMappings().get(nodeName) : null;
            log.info("[allRedeploy] 재배포 시작: node={}, services={}, profileId={}",
                    nodeName, services.stream().map(ParsedService::getServiceName).toList(), profileId);

            List<String> containerIds = new ArrayList<>();
            for (ParsedService svc : services) {
                log.info("[allRedeploy] 서비스 배포: node={}, name={}, image={}, container={}",
                        nodeName, svc.getServiceName(), svc.getImage(), svc.getContainerName());
                containerIds.add(deployer.deployWithDefinitionId(svc, profileId, nodeId, def.getId()));
            }

            long duration = System.currentTimeMillis() - startMs;
            log.info("[allRedeploy] 완료: defId={}, node={}, deployed={}개, duration={}ms",
                    id, nodeName, containerIds.size(), duration);
            return ResponseEntity.ok(Map.of("status", "redeployed", "node", nodeName, "containerIds", containerIds));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startMs;
            log.error("[allRedeploy] 실패: defId={}, node={}, duration={}ms — {}",
                    id, nodeName, duration, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("재배포 실패: " + e.getMessage());
        }
    }

    /** 개별 컨테이너 재배포 — 해당 컨테이너만 stop+remove 후 최신 설정으로 재생성 */
    @PostMapping("/{id}/containers/{containerId}/redeploy")
    public ResponseEntity<?> redeployContainer(@PathVariable String id, @PathVariable String containerId,
                                                @RequestParam(required = false) List<String> activeProfiles) {
        Optional<ServiceDefinition> maybe = repository.findById(id);
        if (maybe.isEmpty()) return ResponseEntity.notFound().build();

        ServiceDefinition def = maybe.get();
        Set<String> nodeIds = resolveNodeIds(def);
        long startMs = System.currentTimeMillis();
        log.info("[containerRedeploy] 시작: defId={}, name={}, containerId={}, profiles={}, 후보 노드={}",
                id, def.getName(), containerId, activeProfiles, nodeIds);

        for (String nodeId : nodeIds) {
            String nodeName = resolveNodeName(nodeId);
            DockerClient client = resolveClient(nodeId);
            try {
                var inspect = client.inspectContainerCmd(containerId).exec();
                if (inspect == null) {
                    log.debug("[containerRedeploy] inspect null — 다른 노드 시도: node={}, containerId={}",
                            nodeName, containerId);
                    continue;
                }

                String containerName = inspect.getName();
                if (containerName != null && containerName.startsWith("/")) containerName = containerName.substring(1);
                String oldImage = inspect.getConfig() != null ? inspect.getConfig().getImage() : null;
                log.info("[containerRedeploy] 컨테이너 발견: node={}, container={} ({}), image={}",
                        nodeName, containerName, containerId, oldImage);

                // 1. stop + remove
                if (inspect.getState() != null && Boolean.TRUE.equals(inspect.getState().getRunning())) {
                    log.info("[containerRedeploy] stop: node={}, container={}", nodeName, containerName);
                    client.stopContainerCmd(containerId).exec();
                } else {
                    log.info("[containerRedeploy] 이미 중지됨, stop 스킵: node={}, container={}",
                            nodeName, containerName);
                }
                log.info("[containerRedeploy] remove: node={}, container={}", nodeName, containerName);
                client.removeContainerCmd(containerId).exec();

                // 2. compose에서 해당 서비스 찾아서 재배포
                List<ParsedService> services = ComposeParser.parse(def.getComposeYaml(), activeProfiles);
                String profileId = def.getNodeEnvMappings() != null ? def.getNodeEnvMappings().get(nodeName) : null;

                // 컨테이너 이름으로 매칭되는 서비스 찾기
                String finalContainerName = containerName;
                ParsedService matchedSvc = services.stream()
                        .filter(s -> finalContainerName != null && finalContainerName.contains(s.getContainerName() != null ? s.getContainerName() : s.getServiceName()))
                        .findFirst()
                        .orElse(services.isEmpty() ? null : services.get(0));

                if (matchedSvc != null) {
                    log.info("[containerRedeploy] 매칭 서비스: name={}, image={}, container={} → 배포 시작",
                            matchedSvc.getServiceName(), matchedSvc.getImage(), matchedSvc.getContainerName());
                    String newId = deployer.deployWithDefinitionId(matchedSvc, profileId, nodeId, def.getId());
                    long duration = System.currentTimeMillis() - startMs;
                    log.info("[containerRedeploy] 완료: defId={}, node={}, container={}, oldId={}, newId={}, duration={}ms",
                            id, nodeName, containerName, containerId, newId, duration);
                    return ResponseEntity.ok(Map.of("status", "redeployed", "containerId", newId));
                }

                log.warn("[containerRedeploy] 매칭 서비스 없음, 삭제만 수행: node={}, container={}",
                        nodeName, containerName);
                return ResponseEntity.ok(Map.of("status", "removed", "message", "매칭 서비스를 찾을 수 없어 삭제만 수행"));
            } catch (Exception e) {
                log.debug("[containerRedeploy] 노드 {} 에서 실패 (다른 노드 시도): {}", nodeName, e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startMs;
        log.warn("[containerRedeploy] 컨테이너 미발견: defId={}, containerId={}, duration={}ms",
                id, containerId, duration);
        return ResponseEntity.notFound().build();
    }

    private ResponseEntity<?> executeOnNode(String defId, String nodeName, String action) {
        Optional<ServiceDefinition> maybe = repository.findById(defId);
        if (maybe.isEmpty()) return ResponseEntity.notFound().build();

        String nodeId = "local".equals(nodeName) ? null : resolveNodeId(nodeName);
        DockerClient client = resolveClient(nodeId);
        long startMs = System.currentTimeMillis();
        String tag = "[all" + Character.toUpperCase(action.charAt(0)) + action.substring(1) + "]";
        log.info("{} 시작: defId={}, name={}, node={}",
                tag, defId, maybe.get().getName(), nodeName);

        int count = 0, skipped = 0, failed = 0;

        try {
            var containers = client.listContainersCmd()
                    .withShowAll(true)
                    .withLabelFilter(Map.of("kite.service-definition-id", defId))
                    .exec();
            log.info("{} 대상 {}개 (node={})", tag, containers.size(), nodeName);

            for (var c : containers) {
                String cName = c.getNames() != null && c.getNames().length > 0
                        ? c.getNames()[0].replaceFirst("^/", "") : c.getId();
                try {
                    if ("stop".equals(action)) {
                        if ("running".equals(c.getState())) {
                            log.info("{} stop 호출: node={}, container={} ({})",
                                    tag, nodeName, cName, c.getId());
                            client.stopContainerCmd(c.getId()).exec();
                            count++;
                        } else {
                            log.info("{} 이미 중지됨, 스킵: node={}, container={} (state={})",
                                    tag, nodeName, cName, c.getState());
                            skipped++;
                        }
                    } else if ("restart".equals(action)) {
                        log.info("{} restart 호출: node={}, container={} ({}, image={})",
                                tag, nodeName, cName, c.getId(), c.getImage());
                        client.restartContainerCmd(c.getId()).exec();
                        count++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.warn("{} {} 실패: node={}, container={} ({}) — {}",
                            tag, action, nodeName, cName, c.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startMs;
            log.error("{} 실패: defId={}, node={}, duration={}ms — {}",
                    tag, defId, nodeName, duration, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(action + " 실패: " + e.getMessage());
        }

        long duration = System.currentTimeMillis() - startMs;
        log.info("{} 완료: defId={}, node={}, success={}, skipped={}, failed={}, duration={}ms",
                tag, defId, nodeName, count, skipped, failed, duration);
        return ResponseEntity.ok(Map.of("status", action + "ped", "node", nodeName, "count", count));
    }

    private ResponseEntity<?> executeOnContainer(String defId, String containerId, String action) {
        Optional<ServiceDefinition> maybe = repository.findById(defId);
        if (maybe.isEmpty()) return ResponseEntity.notFound().build();

        // 모든 노드에서 컨테이너 찾기
        Set<String> nodeIds = resolveNodeIds(maybe.get());
        long startMs = System.currentTimeMillis();
        String tag = "[container" + Character.toUpperCase(action.charAt(0)) + action.substring(1) + "]";
        log.info("{} 시작: defId={}, name={}, containerId={}, 후보 노드={}",
                tag, defId, maybe.get().getName(), containerId, nodeIds);

        for (String nodeId : nodeIds) {
            String nodeName = resolveNodeName(nodeId);
            DockerClient client = resolveClient(nodeId);
            try {
                var inspect = client.inspectContainerCmd(containerId).exec();
                if (inspect != null) {
                    String cName = inspect.getName() != null ? inspect.getName().replaceFirst("^/", "") : containerId;
                    String image = inspect.getConfig() != null ? inspect.getConfig().getImage() : "?";
                    boolean running = inspect.getState() != null && Boolean.TRUE.equals(inspect.getState().getRunning());

                    if ("stop".equals(action)) {
                        if (running) {
                            log.info("{} stop 호출: node={}, container={} ({}, image={})",
                                    tag, nodeName, cName, containerId, image);
                            client.stopContainerCmd(containerId).exec();
                        } else {
                            log.info("{} 이미 중지됨, stop 스킵: node={}, container={}",
                                    tag, nodeName, cName);
                        }
                    } else if ("restart".equals(action)) {
                        log.info("{} restart 호출: node={}, container={} ({}, image={}, wasRunning={})",
                                tag, nodeName, cName, containerId, image, running);
                        client.restartContainerCmd(containerId).exec();
                    }

                    long duration = System.currentTimeMillis() - startMs;
                    log.info("{} 완료: defId={}, node={}, container={}, duration={}ms",
                            tag, defId, nodeName, cName, duration);
                    return ResponseEntity.ok(Map.of("status", action + "ped", "containerId", containerId));
                }
            } catch (Exception e) {
                log.debug("{} 노드 {} 에서 실패 (다른 노드 시도): {}", tag, nodeName, e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startMs;
        log.warn("{} 컨테이너 미발견: defId={}, containerId={}, duration={}ms",
                tag, defId, containerId, duration);
        return ResponseEntity.notFound().build();
    }

    private Set<String> resolveNodeIds(ServiceDefinition def) {
        Set<String> nodeIds = new LinkedHashSet<>();
        Map<String, String> mappings = def.getNodeEnvMappings();
        if (mappings == null || mappings.isEmpty()) {
            nodeIds.add(null);
        } else {
            for (String nodeName : mappings.keySet()) {
                nodeIds.add(nodeName.isEmpty() ? null : resolveNodeId(nodeName));
            }
        }
        return nodeIds;
    }

    private String resolveNodeName(String nodeId) {
        if (nodeId == null) return "local";
        return nodeRegistry.findById(nodeId)
                .map(n -> n.getName())
                .orElse(nodeId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private String resolveNodeId(String nodeName) {
        return nodeRegistry.findAll().stream()
                .filter(n -> n.getName().equals(nodeName))
                .findFirst()
                .map(n -> n.getId())
                .orElse(null);
    }

    private DockerClient resolveClient(String nodeId) {
        if (nodeId == null) return dockerClient;
        return nodeRegistry.findById(nodeId)
                .map(nodeClientFactory::createClient)
                .orElse(dockerClient);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> buildNodeEnvMappings(Map<String, Object> body) {
        // 새 형식: nodeEnvMappings 직접 전달
        if (body.containsKey("nodeEnvMappings") && body.get("nodeEnvMappings") instanceof Map<?,?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(k.toString(), v == null ? null : v.toString()));
            return result;
        }
        // 하위호환: envProfileId + nodeNames → nodeEnvMappings 변환
        String profileId = asString(body.get("envProfileId"));
        List<String> nodes = asStringList(body.get("nodeNames"));
        Map<String, String> result = new LinkedHashMap<>();
        if (nodes.isEmpty()) {
            if (profileId != null) result.put("", profileId);
        } else {
            for (String node : nodes) {
                result.put(node, profileId);
            }
        }
        return result;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof List<?> list) return list.stream().map(Object::toString).toList();
        return new ArrayList<>();
    }
}
