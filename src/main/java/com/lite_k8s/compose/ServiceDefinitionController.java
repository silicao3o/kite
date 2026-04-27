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
            List<String> containerIds = new ArrayList<>();

            Map<String, String> mappings = def.getNodeEnvMappings();
            if (mappings == null || mappings.isEmpty()) {
                // 매핑 없으면 로컬에 배포
                for (ParsedService svc : services) {
                    containerIds.add(deployer.deployWithDefinitionId(svc, null, null, def.getId()));
                }
            } else {
                // 각 노드별로 매핑된 profileId로 배포
                for (Map.Entry<String, String> entry : mappings.entrySet()) {
                    String nodeName = entry.getKey();
                    String profileId = entry.getValue();
                    String nodeId = nodeName.isEmpty() ? null : resolveNodeId(nodeName);
                    for (ParsedService svc : services) {
                        containerIds.add(deployer.deployWithDefinitionId(svc, profileId, nodeId, def.getId()));
                    }
                }
            }

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
