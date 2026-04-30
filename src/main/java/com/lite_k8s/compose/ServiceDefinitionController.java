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
        try {
            List<ParsedService> services = ComposeParser.parse(def.getComposeYaml(), activeProfiles);
            List<String> containerIds = new CopyOnWriteArrayList<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            Map<String, String> mappings = def.getNodeEnvMappings();
            if (mappings == null || mappings.isEmpty()) {
                for (ParsedService svc : services) {
                    futures.add(CompletableFuture.runAsync(() ->
                            containerIds.add(deployer.deployWithDefinitionId(svc, null, null, def.getId()))));
                }
            } else {
                for (Map.Entry<String, String> entry : mappings.entrySet()) {
                    String nodeName = entry.getKey();
                    String profileId = entry.getValue();
                    String nodeId = nodeName.isEmpty() ? null : resolveNodeId(nodeName);
                    for (ParsedService svc : services) {
                        futures.add(CompletableFuture.runAsync(() ->
                                containerIds.add(deployer.deployWithDefinitionId(svc, profileId, nodeId, def.getId()))));
                    }
                }
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            def.setStatus(ServiceDefinition.Status.DEPLOYED);
            repository.save(def);

            return ResponseEntity.ok(Map.of("status", "deployed", "containerIds", containerIds));
        } catch (Exception e) {
            log.error("서비스 배포 실패: {}", def.getName(), e);
            return ResponseEntity.internalServerError().body("배포 실패: " + e.getMessage());
        }
    }

    /** 서비스 중지 — 해당 정의로 생성된 컨테이너 stop + remove */
    @PostMapping("/{id}/stop")
    public ResponseEntity<?> stop(@PathVariable String id) {
        Optional<ServiceDefinition> maybe = repository.findById(id);
        if (maybe.isEmpty()) return ResponseEntity.notFound().build();

        ServiceDefinition def = maybe.get();
        try {
            int totalRemoved = 0;

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

            for (String nodeId : nodeIds) {
                DockerClient client = resolveClient(nodeId);
                var containers = client.listContainersCmd()
                        .withShowAll(true)
                        .withLabelFilter(Map.of("kite.service-definition-id", id))
                        .exec();

                for (var container : containers) {
                    try {
                        if (container.getState() != null && container.getState().contains("running")) {
                            client.stopContainerCmd(container.getId()).exec();
                        }
                        client.removeContainerCmd(container.getId()).exec();
                        totalRemoved++;
                    } catch (Exception e) {
                        log.warn("컨테이너 정리 실패: {}", container.getId(), e);
                    }
                }
            }

            def.setStatus(ServiceDefinition.Status.STOPPED);
            repository.save(def);

            return ResponseEntity.ok(Map.of("status", "stopped", "removed", totalRemoved));
        } catch (Exception e) {
            log.error("서비스 중지 실패: {}", def.getName(), e);
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

        try {
            // 1. 기존 컨테이너 stop + remove
            var containers = client.listContainersCmd()
                    .withShowAll(true)
                    .withLabelFilter(Map.of("kite.service-definition-id", id))
                    .exec();
            for (var c : containers) {
                try {
                    if ("running".equals(c.getState())) client.stopContainerCmd(c.getId()).exec();
                    client.removeContainerCmd(c.getId()).exec();
                } catch (Exception e) {
                    log.warn("재배포 전 컨테이너 정리 실패: {}", c.getId(), e);
                }
            }

            // 2. 최신 설정으로 재배포
            List<ParsedService> services = ComposeParser.parse(def.getComposeYaml(), activeProfiles);
            String profileId = def.getNodeEnvMappings() != null ? def.getNodeEnvMappings().get(nodeName) : null;
            List<String> containerIds = new ArrayList<>();
            for (ParsedService svc : services) {
                containerIds.add(deployer.deployWithDefinitionId(svc, profileId, nodeId, def.getId()));
            }

            return ResponseEntity.ok(Map.of("status", "redeployed", "node", nodeName, "containerIds", containerIds));
        } catch (Exception e) {
            log.error("노드 재배포 실패: {} node={}", def.getName(), nodeName, e);
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

        for (String nodeId : nodeIds) {
            DockerClient client = resolveClient(nodeId);
            try {
                var inspect = client.inspectContainerCmd(containerId).exec();
                if (inspect == null) continue;

                String containerName = inspect.getName();
                if (containerName != null && containerName.startsWith("/")) containerName = containerName.substring(1);

                // 1. stop + remove
                if (inspect.getState() != null && Boolean.TRUE.equals(inspect.getState().getRunning())) {
                    client.stopContainerCmd(containerId).exec();
                }
                client.removeContainerCmd(containerId).exec();

                // 2. compose에서 해당 서비스 찾아서 재배포
                List<ParsedService> services = ComposeParser.parse(def.getComposeYaml(), activeProfiles);
                String nodeName = resolveNodeName(nodeId);
                String profileId = def.getNodeEnvMappings() != null ? def.getNodeEnvMappings().get(nodeName) : null;

                // 컨테이너 이름으로 매칭되는 서비스 찾기
                String finalContainerName = containerName;
                ParsedService matchedSvc = services.stream()
                        .filter(s -> finalContainerName != null && finalContainerName.contains(s.getContainerName() != null ? s.getContainerName() : s.getServiceName()))
                        .findFirst()
                        .orElse(services.isEmpty() ? null : services.get(0));

                if (matchedSvc != null) {
                    String newId = deployer.deployWithDefinitionId(matchedSvc, profileId, nodeId, def.getId());
                    return ResponseEntity.ok(Map.of("status", "redeployed", "containerId", newId));
                }

                return ResponseEntity.ok(Map.of("status", "removed", "message", "매칭 서비스를 찾을 수 없어 삭제만 수행"));
            } catch (Exception ignored) {}
        }

        return ResponseEntity.notFound().build();
    }

    private ResponseEntity<?> executeOnNode(String defId, String nodeName, String action) {
        Optional<ServiceDefinition> maybe = repository.findById(defId);
        if (maybe.isEmpty()) return ResponseEntity.notFound().build();

        String nodeId = "local".equals(nodeName) ? null : resolveNodeId(nodeName);
        DockerClient client = resolveClient(nodeId);
        int count = 0;

        try {
            var containers = client.listContainersCmd()
                    .withShowAll(true)
                    .withLabelFilter(Map.of("kite.service-definition-id", defId))
                    .exec();
            for (var c : containers) {
                try {
                    if ("stop".equals(action)) {
                        if ("running".equals(c.getState())) {
                            client.stopContainerCmd(c.getId()).exec();
                            count++;
                        }
                    } else if ("restart".equals(action)) {
                        client.restartContainerCmd(c.getId()).exec();
                        count++;
                    }
                } catch (Exception e) {
                    log.warn("컨테이너 {} 실패 ({}): {}", action, c.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(action + " 실패: " + e.getMessage());
        }

        return ResponseEntity.ok(Map.of("status", action + "ped", "node", nodeName, "count", count));
    }

    private ResponseEntity<?> executeOnContainer(String defId, String containerId, String action) {
        Optional<ServiceDefinition> maybe = repository.findById(defId);
        if (maybe.isEmpty()) return ResponseEntity.notFound().build();

        // 모든 노드에서 컨테이너 찾기
        Set<String> nodeIds = resolveNodeIds(maybe.get());
        for (String nodeId : nodeIds) {
            DockerClient client = resolveClient(nodeId);
            try {
                var inspect = client.inspectContainerCmd(containerId).exec();
                if (inspect != null) {
                    if ("stop".equals(action)) {
                        client.stopContainerCmd(containerId).exec();
                    } else if ("restart".equals(action)) {
                        client.restartContainerCmd(containerId).exec();
                    }
                    return ResponseEntity.ok(Map.of("status", action + "ped", "containerId", containerId));
                }
            } catch (Exception ignored) {}
        }

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
