package com.lite_k8s.compose;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/service-definitions")
@RequiredArgsConstructor
public class ServiceDefinitionController {

    private final ServiceDefinitionRepository repository;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String name = asString(body.get("name"));
        String composeYaml = asString(body.get("composeYaml"));
        if (name == null || name.isBlank()) return ResponseEntity.badRequest().body("name은 필수입니다");
        if (composeYaml == null || composeYaml.isBlank()) return ResponseEntity.badRequest().body("composeYaml은 필수입니다");

        ServiceDefinition def = ServiceDefinition.builder()
                .name(name)
                .composeYaml(composeYaml)
                .envProfileId(asString(body.get("envProfileId")))
                .nodeNames(asStringList(body.get("nodeNames")))
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
        if (body.containsKey("envProfileId")) def.setEnvProfileId(asString(body.get("envProfileId")));
        if (body.containsKey("nodeNames")) def.setNodeNames(asStringList(body.get("nodeNames")));
        if (body.containsKey("status")) {
            try { def.setStatus(ServiceDefinition.Status.valueOf(asString(body.get("status")))); }
            catch (IllegalArgumentException ignored) {}
        }

        return ResponseEntity.ok(repository.save(def));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
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
