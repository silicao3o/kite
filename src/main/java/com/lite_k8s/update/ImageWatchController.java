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
    private final ImageUpdatePoller poller;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String image = asString(body.get("image"));
        if (image == null || image.isBlank()) {
            return ResponseEntity.badRequest().body("image는 필수입니다");
        }

        ImageWatchEntity.WatchMode mode = parseMode(asString(body.get("mode")));
        ImageWatchEntity entity = ImageWatchEntity.builder()
                .image(image)
                .tag(asStringOrDefault(body.get("tag"), "latest"))
                .containerPattern(asString(body.get("containerPattern")))
                .nodeNames(asStringList(body.get("nodeNames")))
                .pollIntervalSeconds(mode == ImageWatchEntity.WatchMode.TRIGGER ? null : asInt(body.get("pollIntervalSeconds"), 300))
                .maxUnavailable(asInt(body.get("maxUnavailable"), 1))
                .mode(mode)
                .ghcrToken(asString(body.get("ghcrToken")))
                .enabled(true)
                .build();

        ImageWatchEntity saved = watchService.save(entity);
        poller.scheduleWatch(saved);
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
        if (body.containsKey("pollIntervalSeconds")) entity.setPollIntervalSeconds(asInt(body.get("pollIntervalSeconds"), entity.getPollIntervalSeconds() != null ? entity.getPollIntervalSeconds() : 300));
        if (body.containsKey("maxUnavailable")) entity.setMaxUnavailable(asInt(body.get("maxUnavailable"), entity.getMaxUnavailable()));
        if (body.containsKey("mode")) {
            ImageWatchEntity.WatchMode newMode = parseMode(asString(body.get("mode")));
            entity.setMode(newMode);
            if (newMode == ImageWatchEntity.WatchMode.TRIGGER) entity.setPollIntervalSeconds(null);
        }
        if (body.containsKey("enabled")) entity.setEnabled(asBoolean(body.get("enabled")));
        if (body.containsKey("ghcrToken")) {
            String token = asString(body.get("ghcrToken"));
            if (!isMasked(token)) {
                entity.setGhcrToken(token);
            }
        }
        ImageWatchEntity saved = watchService.save(entity);
        poller.scheduleWatch(saved);
        return ResponseEntity.ok(toMaskedResponse(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        watchService.delete(id);
        poller.cancelSchedule(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    public List<ImageUpdateHistoryEntity> history(@PathVariable String id) {
        return historyService.findByWatchId(id);
    }

    /** 특정 와치 즉시 트리거 */
    @PostMapping("/{id}/trigger")
    public ResponseEntity<?> trigger(@PathVariable String id) {
        Optional<ImageWatchEntity> maybe = watchService.findById(id);
        if (maybe.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        poller.checkWatch(maybe.get());
        return ResponseEntity.ok(Map.of("status", "triggered", "watchId", id));
    }

    /** 전체 활성 와치 즉시 트리거 */
    @PostMapping("/trigger-all")
    public ResponseEntity<?> triggerAll() {
        poller.triggerAll();
        return ResponseEntity.ok(Map.of("status", "triggered"));
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
        map.put("mode", entity.getMode() != null ? entity.getMode().name() : "POLLING");
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

    private boolean asBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }

    private ImageWatchEntity.WatchMode parseMode(String value) {
        if (value == null) return ImageWatchEntity.WatchMode.POLLING;
        try { return ImageWatchEntity.WatchMode.valueOf(value.toUpperCase()); }
        catch (IllegalArgumentException e) { return ImageWatchEntity.WatchMode.POLLING; }
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
