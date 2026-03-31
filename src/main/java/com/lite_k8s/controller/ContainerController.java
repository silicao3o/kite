package com.lite_k8s.controller;

import com.lite_k8s.service.ContainerRecreateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/containers")
public class ContainerController {

    private final ContainerRecreateService containerRecreateService;

    @PostMapping("/{id}/update-image")
    public ResponseEntity<String> updateImage(
            @PathVariable String id,
            @RequestParam(required = false) String nodeId) {
        try {
            containerRecreateService.pullAndRecreate(id, nodeId);
            return ResponseEntity.ok("이미지 업데이트 완료");
        } catch (Exception e) {
            log.error("이미지 업데이트 실패: containerId={}", id, e);
            return ResponseEntity.internalServerError().body("업데이트 실패: " + e.getMessage());
        }
    }
}
