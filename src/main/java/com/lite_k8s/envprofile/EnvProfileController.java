package com.lite_k8s.envprofile;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/env-profiles")
@RequiredArgsConstructor
public class EnvProfileController {

    private final EnvProfileService service;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String name = asString(body.get("name"));
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body("name은 필수입니다");
        }

        EnvProfile profile = EnvProfile.builder()
                .name(name)
                .type(parseType(asString(body.get("type"))))
                .description(asString(body.get("description")))
                .build();

        EnvProfile saved = service.saveProfile(profile);

        // 엔트리 저장
        Object entriesObj = body.get("entries");
        if (entriesObj instanceof List<?> entries) {
            for (Object entryObj : entries) {
                if (entryObj instanceof Map<?, ?> entryMap) {
                    EnvProfileEntry entry = EnvProfileEntry.builder()
                            .profileId(saved.getId())
                            .key(asString(entryMap.get("key")))
                            .value(asString(entryMap.get("value")))
                            .secret(Boolean.TRUE.equals(entryMap.get("secret")))
                            .build();
                    service.saveEntry(entry);
                }
            }
        }

        return ResponseEntity.status(201).body(toResponse(saved));
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return service.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        return service.findById(id)
                .map(profile -> {
                    Map<String, Object> response = toResponse(profile);
                    response.put("entries", service.getEntries(id));
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Optional<EnvProfile> maybe = service.findById(id);
        if (maybe.isEmpty()) return ResponseEntity.notFound().build();

        EnvProfile profile = maybe.get();
        if (body.containsKey("name")) profile.setName(asString(body.get("name")));
        if (body.containsKey("description")) profile.setDescription(asString(body.get("description")));
        if (body.containsKey("type")) profile.setType(parseType(asString(body.get("type"))));

        return ResponseEntity.ok(toResponse(service.saveProfile(profile)));
    }

    @PutMapping("/{id}/entries/{key}")
    public ResponseEntity<?> updateEntry(@PathVariable String id, @PathVariable String key,
                                          @RequestBody Map<String, Object> body) {
        Optional<EnvProfileEntry> maybe = service.findEntryByKey(id, key);
        if (maybe.isEmpty()) return ResponseEntity.notFound().build();

        EnvProfileEntry entry = maybe.get();
        if (body.containsKey("value")) {
            entry.setValue(asString(body.get("value")));
            // secret 엔트리면 Service에서 암호화 처리
        }
        if (body.containsKey("secret")) {
            entry.setSecret(Boolean.TRUE.equals(body.get("secret")));
        }

        service.saveEntry(entry);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toResponse(EnvProfile profile) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", profile.getId());
        map.put("name", profile.getName());
        map.put("type", profile.getType().name());
        map.put("description", profile.getDescription());
        map.put("enabled", profile.isEnabled());
        map.put("createdAt", profile.getCreatedAt());
        return map;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private EnvProfile.ProfileType parseType(String value) {
        if (value == null) return EnvProfile.ProfileType.DATABASE;
        try { return EnvProfile.ProfileType.valueOf(value.toUpperCase()); }
        catch (IllegalArgumentException e) { return EnvProfile.ProfileType.DATABASE; }
    }
}
