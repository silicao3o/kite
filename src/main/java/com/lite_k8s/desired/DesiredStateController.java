package com.lite_k8s.desired;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/desired-state/services")
@RequiredArgsConstructor
public class DesiredStateController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DesiredStateService service;

    @PostMapping
    public ServiceSpecResponse addService(@RequestBody AddServiceRequest request) {
        DesiredServiceSpecEntity entity = DesiredServiceSpecEntity.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName())
                .image(request.getImage())
                .replicas(request.getReplicas())
                .containerNamePrefix(request.getContainerNamePrefix() != null
                        ? request.getContainerNamePrefix() : request.getName())
                .nodeName(request.getNodeName())
                .envJson(toJson(request.getEnv()))
                .portsJson(toJson(request.getPorts()))
                .enabled(true)
                .build();
        return ServiceSpecResponse.from(service.save(entity));
    }

    @GetMapping
    public List<ServiceSpecResponse> listServices() {
        return service.findAll().stream()
                .map(ServiceSpecResponse::from)
                .collect(Collectors.toList());
    }

    @PutMapping("/{id}")
    public ServiceSpecResponse updateService(@PathVariable String id,
                                              @RequestBody UpdateServiceRequest request) {
        DesiredServiceSpecEntity entity = service.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found: " + id));
        if (request.getReplicas() > 0) entity.setReplicas(request.getReplicas());
        if (request.getImage() != null) entity.setImage(request.getImage());
        if (request.getNodeName() != null) entity.setNodeName(request.getNodeName());
        if (request.getEnv() != null) entity.setEnvJson(toJson(request.getEnv()));
        if (request.getPorts() != null) entity.setPortsJson(toJson(request.getPorts()));
        if (request.isEnable()) entity.setEnabled(true);
        return ServiceSpecResponse.from(service.save(entity));
    }

    @DeleteMapping("/{id}")
    public void deleteService(@PathVariable String id) {
        service.disable(id);
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Getter
    @Setter
    public static class AddServiceRequest {
        private String name;
        private String image;
        private int replicas = 1;
        private String containerNamePrefix;
        private String nodeName;
        private List<String> env;
        private List<String> ports;
    }

    @Getter
    @Setter
    public static class UpdateServiceRequest {
        private int replicas;
        private String image;
        private String nodeName;
        private List<String> env;
        private List<String> ports;
        private boolean enable; // enabled=true로 복원
    }

    @Getter
    public static class ServiceSpecResponse {
        private final String id;
        private final String name;
        private final String image;
        private final int replicas;
        private final String containerNamePrefix;
        private final String nodeName;
        private final boolean enabled;

        private ServiceSpecResponse(String id, String name, String image, int replicas,
                                    String containerNamePrefix, String nodeName, boolean enabled) {
            this.id = id;
            this.name = name;
            this.image = image;
            this.replicas = replicas;
            this.containerNamePrefix = containerNamePrefix;
            this.nodeName = nodeName;
            this.enabled = enabled;
        }

        static ServiceSpecResponse from(DesiredServiceSpecEntity e) {
            return new ServiceSpecResponse(
                    e.getId(), e.getName(), e.getImage(), e.getReplicas(),
                    e.getContainerNamePrefix(), e.getNodeName(), e.isEnabled());
        }
    }
}
