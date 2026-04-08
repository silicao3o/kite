package com.lite_k8s.controller;

import com.lite_k8s.model.EmailSubscriptionEntity;
import com.lite_k8s.service.EmailSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/email-subscriptions")
@RequiredArgsConstructor
public class EmailSubscriptionController {

    private final EmailSubscriptionService service;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String email = asString(body.get("email"));
        String containerPattern = asString(body.get("containerPattern"));
        String nodeName = asString(body.get("nodeName"));
        boolean notifyIntentional = asBoolean(body.get("notifyIntentional"));

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("email은 필수입니다");
        }
        if (isBlank(containerPattern) && isBlank(nodeName)) {
            return ResponseEntity.badRequest()
                    .body("containerPattern 또는 nodeName 중 최소 하나는 필수입니다");
        }

        EmailSubscriptionEntity entity = EmailSubscriptionEntity.builder()
                .email(email)
                .containerPattern(nullIfBlank(containerPattern))
                .nodeName(nullIfBlank(nodeName))
                .notifyIntentional(notifyIntentional)
                .enabled(true)
                .build();
        return ResponseEntity.status(201).body(service.save(entity));
    }

    @GetMapping
    public List<EmailSubscriptionEntity> list() {
        return service.findAll();
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Optional<EmailSubscriptionEntity> maybe = service.findById(id);
        if (maybe.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        EmailSubscriptionEntity entity = maybe.get();
        if (body.containsKey("email")) entity.setEmail(asString(body.get("email")));
        if (body.containsKey("containerPattern")) entity.setContainerPattern(nullIfBlank(asString(body.get("containerPattern"))));
        if (body.containsKey("nodeName")) entity.setNodeName(nullIfBlank(asString(body.get("nodeName"))));
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

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String nullIfBlank(String s) {
        return isBlank(s) ? null : s;
    }
}
