package com.lite_k8s.controller;

import com.lite_k8s.model.SelfHealingRuleEntity;
import com.lite_k8s.service.SelfHealingRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/self-healing/rules")
@RequiredArgsConstructor
public class SelfHealingRuleController {

    private final SelfHealingRuleService service;

    @PostMapping
    public ResponseEntity<SelfHealingRuleEntity> create(@RequestBody Map<String, Object> body) {
        SelfHealingRuleEntity entity = SelfHealingRuleEntity.builder()
                .namePattern(asString(body.get("namePattern")))
                .maxRestarts(asInt(body.get("maxRestarts"), 3))
                .restartDelaySeconds(asInt(body.get("restartDelaySeconds"), 0))
                .nodeName(asString(body.get("nodeName")))
                .enabled(true)
                .build();
        SelfHealingRuleEntity saved = service.save(entity);
        return ResponseEntity.status(201).body(saved);
    }

    @GetMapping
    public List<SelfHealingRuleEntity> list() {
        return service.findAll();
    }

    @PutMapping("/{id}")
    public ResponseEntity<SelfHealingRuleEntity> update(@PathVariable String id,
                                                         @RequestBody Map<String, Object> body) {
        Optional<SelfHealingRuleEntity> maybe = service.findById(id);
        if (maybe.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        SelfHealingRuleEntity entity = maybe.get();
        if (body.containsKey("namePattern")) entity.setNamePattern(asString(body.get("namePattern")));
        if (body.containsKey("maxRestarts")) entity.setMaxRestarts(asInt(body.get("maxRestarts"), entity.getMaxRestarts()));
        if (body.containsKey("restartDelaySeconds")) entity.setRestartDelaySeconds(asInt(body.get("restartDelaySeconds"), entity.getRestartDelaySeconds()));
        if (body.containsKey("nodeName")) entity.setNodeName(asString(body.get("nodeName")));
        return ResponseEntity.ok(service.save(entity));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.disable(id);
        return ResponseEntity.noContent().build();
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private int asInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(value.toString());
    }
}
