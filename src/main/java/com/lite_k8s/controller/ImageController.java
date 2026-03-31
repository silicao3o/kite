package com.lite_k8s.controller;

import com.lite_k8s.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/images")
public class ImageController {

    private final ImageService imageService;

    @DeleteMapping
    public ResponseEntity<String> deleteImage(
            @RequestParam String name,
            @RequestParam(required = false) String nodeId) {
        try {
            imageService.deleteImage(name, nodeId);
            return ResponseEntity.ok("이미지 삭제 완료");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("삭제 실패: " + e.getMessage());
        }
    }
}
