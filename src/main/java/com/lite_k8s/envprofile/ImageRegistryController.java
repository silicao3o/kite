package com.lite_k8s.envprofile;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/image-registry")
@RequiredArgsConstructor
public class ImageRegistryController {

    private final ImageRegistryRepository repository;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String image = body.get("image") != null ? body.get("image").toString().trim() : null;
        if (image == null || image.isBlank()) {
            return ResponseEntity.badRequest().body("image는 필수입니다");
        }

        ImageRegistry entity = ImageRegistry.builder()
                .image(image)
                .alias(body.get("alias") != null ? body.get("alias").toString().trim() : null)
                .description(body.get("description") != null ? body.get("description").toString().trim() : null)
                .ghcrToken(body.get("ghcrToken") != null ? body.get("ghcrToken").toString().trim() : null)
                .build();

        return ResponseEntity.status(201).body(repository.save(entity));
    }

    @GetMapping
    public List<ImageRegistry> list() {
        return repository.findAll();
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        return repository.findById(id)
                .map(existing -> {
                    if (body.containsKey("image")) existing.setImage(body.get("image").trim());
                    if (body.containsKey("alias")) existing.setAlias(body.get("alias").trim());
                    if (body.containsKey("description")) existing.setDescription(body.get("description").trim());
                    if (body.containsKey("ghcrToken")) existing.setGhcrToken(body.get("ghcrToken").trim());
                    return ResponseEntity.ok(repository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
