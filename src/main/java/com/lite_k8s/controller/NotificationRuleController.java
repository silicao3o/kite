package com.lite_k8s.controller;

import com.lite_k8s.model.NotificationRuleEntity;
import com.lite_k8s.model.NotificationRuleEntity.Mode;
import com.lite_k8s.service.NotificationRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/notification/rules")
@RequiredArgsConstructor
public class NotificationRuleController {

    private final NotificationRuleService service;

    @PostMapping
    public ResponseEntity<NotificationRuleEntity> create(@RequestBody Map<String, Object> body) {
        NotificationRuleEntity entity = NotificationRuleEntity.builder()
                .namePattern(asString(body.get("namePattern")))
                .nodeName(asString(body.get("nodeName")))
                .mode(parseMode(body.get("mode")))
                .notifyIntentional(asBoolean(body.get("notifyIntentional")))
                .enabled(true)
                .build();
        NotificationRuleEntity saved = service.save(entity);
        return ResponseEntity.status(201).body(saved);
    }

    @GetMapping
    public List<NotificationRuleEntity> list() {
        return service.findAll();
    }

    @PutMapping("/{id}")
    public ResponseEntity<NotificationRuleEntity> update(@PathVariable String id,
                                                          @RequestBody Map<String, Object> body) {
        Optional<NotificationRuleEntity> maybe = service.findById(id);
        if (maybe.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        NotificationRuleEntity entity = maybe.get();
        if (body.containsKey("namePattern")) entity.setNamePattern(asString(body.get("namePattern")));
        if (body.containsKey("nodeName")) entity.setNodeName(asString(body.get("nodeName")));
        if (body.containsKey("mode")) entity.setMode(parseMode(body.get("mode")));
        if (body.containsKey("notifyIntentional")) entity.setNotifyIntentional(asBoolean(body.get("notifyIntentional")));
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

    private boolean asBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }

    private Mode parseMode(Object value) {
        if (value == null) return Mode.INCLUDE;
        try {
            return Mode.valueOf(value.toString().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mode.INCLUDE;
        }
    }
}
