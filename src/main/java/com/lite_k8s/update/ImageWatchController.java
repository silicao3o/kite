package com.lite_k8s.update;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
                .maxUnavailable(asInt(body.get("maxUnavailable"), 1))
                .enabled(true)
                .build();

        return ResponseEntity.status(201).body(watchService.save(entity));
    }

    @GetMapping
    public List<ImageWatchEntity> list() {
        return watchService.findAll();
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
        if (body.containsKey("maxUnavailable")) entity.setMaxUnavailable(asInt(body.get("maxUnavailable"), entity.getMaxUnavailable()));
        if (body.containsKey("enabled")) entity.setEnabled(asBoolean(body.get("enabled")));
        return ResponseEntity.ok(watchService.save(entity));
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
}
