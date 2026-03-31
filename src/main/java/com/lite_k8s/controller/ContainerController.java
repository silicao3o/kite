package com.lite_k8s.controller;

import com.github.dockerjava.api.DockerClient;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
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
    private final DockerClient dockerClient;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;

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

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteContainer(
            @PathVariable String id,
            @RequestParam(required = false) String nodeId) {
        try {
            DockerClient client = resolveClient(nodeId);
            client.removeContainerCmd(id).withForce(true).exec();
            log.info("컨테이너 삭제 완료: {}", id);
            return ResponseEntity.ok("컨테이너 삭제 완료");
        } catch (Exception e) {
            log.error("컨테이너 삭제 실패: containerId={}", id, e);
            return ResponseEntity.internalServerError().body("삭제 실패: " + e.getMessage());
        }
    }

    private DockerClient resolveClient(String nodeId) {
        if (nodeId == null) return dockerClient;
        return nodeRegistry.findById(nodeId)
                .map(nodeClientFactory::createClient)
                .orElse(dockerClient);
    }
}
