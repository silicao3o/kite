package com.lite_k8s.update;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/image-watches")
@RequiredArgsConstructor
public class ImageWatchController {

    private final ImageWatchService watchService;
    private final ImageUpdateHistoryService historyService;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String image = asString(body.get("image"));
        if (image == null || image.isBlank()) {
            return ResponseEntity.badRequest().body("image는 필수입니다");
        }

        ImageWatchEntity entity = ImageWatchEntity.builder()
                .image(image)
                .tag(asStringOrDefault(body.get("tag"), "latest"))
                .containerPattern(asString(body.get("containerPattern")))
                .nodeNames(asStringList(body.get("nodeNames")))
                .pollIntervalSeconds(asInteger(body.get("pollIntervalSeconds")))
                .maxUnavailable(asInt(body.get("maxUnavailable"), 1))
                .ghcrToken(asString(body.get("ghcrToken")))
                .enabled(true)
                .build();

        ImageWatchEntity saved = watchService.save(entity);
        return ResponseEntity.status(201).body(toMaskedResponse(saved));
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return watchService.findAll().stream()
                .map(this::toMaskedResponse)
                .toList();
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Optional<ImageWatchEntity> maybe = watchService.findById(id);
        if (maybe.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ImageWatchEntity entity = maybe.get();
        if (body.containsKey("image")) entity.setImage(asString(body.get("image")));
        if (body.containsKey("tag")) entity.setTag(asString(body.get("tag")));
        if (body.containsKey("containerPattern")) entity.setContainerPattern(asString(body.get("containerPattern")));
        if (body.containsKey("nodeNames")) entity.setNodeNames(asStringList(body.get("nodeNames")));
        if (body.containsKey("pollIntervalSeconds")) entity.setPollIntervalSeconds(asInteger(body.get("pollIntervalSeconds")));
        if (body.containsKey("maxUnavailable")) entity.setMaxUnavailable(asInt(body.get("maxUnavailable"), entity.getMaxUnavailable()));
        if (body.containsKey("enabled")) entity.setEnabled(asBoolean(body.get("enabled")));
        if (body.containsKey("ghcrToken")) {
            String token = asString(body.get("ghcrToken"));
            // 마스킹된 값이면 기존 토큰 유지
            if (!isMasked(token)) {
                entity.setGhcrToken(token);
            }
        }
        return ResponseEntity.ok(toMaskedResponse(watchService.save(entity)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        watchService.disable(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    public List<ImageUpdateHistoryEntity> history(@PathVariable String id) {
        return historyService.findByWatchId(id);
    }

    private Map<String, Object> toMaskedResponse(ImageWatchEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("image", entity.getImage());
        map.put("tag", entity.getTag());
        map.put("containerPattern", entity.getContainerPattern());
        map.put("nodeNames", entity.getNodeNames() != null ? entity.getNodeNames() : List.of());
        map.put("pollIntervalSeconds", entity.getPollIntervalSeconds());
        map.put("maxUnavailable", entity.getMaxUnavailable());
        map.put("ghcrToken", maskToken(entity.getGhcrToken()));
        map.put("enabled", entity.isEnabled());
        map.put("createdAt", entity.getCreatedAt());
        return map;
    }

    static String maskToken(String token) {
        if (token == null || token.isEmpty()) return null;
        if (token.length() <= 4) return "****";
        return token.substring(0, 4) + "****";
    }

    private boolean isMasked(String token) {
        return token != null && token.endsWith("****");
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String asStringOrDefault(Object value, String defaultValue) {
        return value == null ? defaultValue : value.toString();
    }

    private int asInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(value.toString()); } catch (NumberFormatException e) { return defaultValue; }
    }

    private Integer asInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(value.toString()); } catch (NumberFormatException e) { return null; }
    }

    private boolean asBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return new ArrayList<>();
    }
}
