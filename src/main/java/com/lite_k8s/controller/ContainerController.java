package com.lite_k8s.controller;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.HostConfig;
import com.lite_k8s.envprofile.EnvProfileResolver;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import com.lite_k8s.service.ContainerRecreateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/containers")
public class ContainerController {

    private final ContainerRecreateService containerRecreateService;
    private final DockerClient dockerClient;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;
    private final MetricsScheduler metricsScheduler;
    private final EnvProfileResolver envProfileResolver;

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

    /** 컨테이너에 env profile 적용 (프로파일 env로 DB 정보만 오버라이드하여 재생성) */
    @PostMapping("/{id}/apply-env-profile")
    public ResponseEntity<String> applyEnvProfile(
            @PathVariable String id,
            @RequestParam(required = false) String nodeId,
            @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<String> profileIds = body.get("profileIds") instanceof List<?> list
                    ? list.stream().map(Object::toString).toList()
                    : List.of();

            if (profileIds.isEmpty()) {
                return ResponseEntity.badRequest().body("profileIds는 필수입니다");
            }

            DockerClient client = resolveClient(nodeId);

            // 1. 기존 컨테이너 설정 조회
            InspectContainerResponse inspect = client.inspectContainerCmd(id).exec();
            String containerName = parseName(inspect.getName());
            String image = inspect.getConfig().getImage();

            // 2. 기존 env + 프로파일 env merge (프로파일이 DB 키 오버라이드)
            String[] existingEnv = inspect.getConfig().getEnv();
            String[] mergedEnv = envProfileResolver.mergeWithExistingEnv(profileIds, existingEnv);

            // 3. 기존 라벨 + kite.env-profile-ids 라벨 추가
            Map<String, String> labels = new HashMap<>(
                    inspect.getConfig().getLabels() != null ? inspect.getConfig().getLabels() : Map.of());
            labels.put(EnvProfileResolver.LABEL_KEY, EnvProfileResolver.buildProfileLabel(profileIds));

            // 4. 기존 컨테이너 중지 + 제거
            client.stopContainerCmd(id).exec();
            client.removeContainerCmd(id).exec();

            // 5. 새 컨테이너 생성 (동일 이미지, 새 env)
            HostConfig hostConfig = inspect.getHostConfig() != null
                    ? inspect.getHostConfig() : HostConfig.newHostConfig();

            CreateContainerResponse response = client.createContainerCmd(image)
                    .withName(containerName)
                    .withHostConfig(hostConfig)
                    .withEnv(mergedEnv)
                    .withLabels(labels)
                    .exec();

            // 6. 시작
            client.startContainerCmd(response.getId()).exec();

            log.info("Env Profile 적용 완료: {} (profiles: {})", containerName, profileIds);
            return ResponseEntity.ok("Env Profile 적용 완료: " + containerName);

        } catch (Exception e) {
            log.error("Env Profile 적용 실패: containerId={}", id, e);
            return ResponseEntity.internalServerError().body("적용 실패: " + e.getMessage());
        }
    }

    private String parseName(String rawName) {
        if (rawName == null) return "unknown";
        return rawName.startsWith("/") ? rawName.substring(1) : rawName;
    }

    private DockerClient resolveClient(String nodeId) {
        if (nodeId == null) return dockerClient;
        return nodeRegistry.findById(nodeId)
                .map(nodeClientFactory::createClient)
                .orElse(dockerClient);
    }
}
