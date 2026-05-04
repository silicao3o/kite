package com.lite_k8s.update;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.lite_k8s.envprofile.ImageRegistryRepository;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import com.lite_k8s.service.OwnActionTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 컨테이너 재생성 (이미지 업데이트 시 사용)
 *
 * 순서: 기존 컨테이너 정보 조회 → stop → remove → 새 이미지로 create → start
 * 실패 시: false 반환 (롤백은 RollingUpdateService가 처리)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContainerRecreator {

    private final DockerClient dockerClient;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;
    private final OwnActionTracker ownActionTracker;
    private final ImageRegistryRepository imageRegistryRepository;
    private final ImageRetentionService imageRetentionService;

    /**
     * 컨테이너를 새 이미지로 재생성 (로컬)
     */
    public boolean recreate(String containerId, String imageRef, String newDigest) {
        return recreate(containerId, imageRef, newDigest, null);
    }

    /**
     * 컨테이너를 새 이미지로 재생성
     *
     * @param containerId 기존 컨테이너 ID
     * @param imageRef    새 이미지 (ghcr.io/owner/app)
     * @param newDigest   새 digest (로깅용)
     * @param nodeId      노드 ID (null = 로컬)
     * @return 성공 여부
     */
    public boolean recreate(String containerId, String imageRef, String newDigest, String nodeId) {
        DockerClient client = resolveClient(nodeId);
        try {
            // 1. 기존 컨테이너 설정 조회
            InspectContainerResponse inspect = client.inspectContainerCmd(containerId).exec();
            String containerName = parseName(inspect.getName());

            log.info("컨테이너 업데이트 시작: {} ({})", containerName, newDigest);

            // 2. 새 이미지 pull — 호출부가 watch 태그/digest로 pin된 ref를 넘겨준다
            pullImage(client, imageRef);

            // 3. 기존 컨테이너 중지
            ownActionTracker.markOwnAction(containerId);
            client.stopContainerCmd(containerId).exec();
            log.debug("컨테이너 중지 완료: {}", containerName);

            // 4. 기존 컨테이너 제거
            client.removeContainerCmd(containerId).exec();
            log.debug("컨테이너 제거 완료: {}", containerName);

            // 5. 새 이미지로 컨테이너 생성
            String newContainerId = createContainer(client, containerName, imageRef, inspect);
            log.debug("컨테이너 생성 완료: {} → {}", containerName, newContainerId);

            // 6. 새 컨테이너 시작
            client.startContainerCmd(newContainerId).exec();
            log.info("컨테이너 업데이트 완료: {} → {}", containerName, newContainerId);

            // 7. 옛 이미지 retention cleanup — 디스크 누적 방지. 실패해도 update 자체는 성공.
            try {
                String repo = stripReference(imageRef);
                int pruned = imageRetentionService.pruneOldImages(client, repo);
                if (pruned > 0) {
                    log.info("retention: {} 옛 이미지 {}개 prune", repo, pruned);
                }
            } catch (Exception e) {
                log.warn("retention: cleanup 중 오류 (update 자체는 성공): {}", containerName, e);
            }

            return true;

        } catch (Exception e) {
            log.error("컨테이너 재생성 실패: {}", containerId, e);
            return false;
        }
    }

    private void pullImage(DockerClient client, String image) {
        log.info("이미지 pull 시작: {}", image);
        PullImageCmd cmd = client.pullImageCmd(image).withPlatform("linux/amd64");
        if (image.startsWith("ghcr.io")) {
            String token = resolveGhcrToken(image);
            if (token != null) {
                String[] parts = image.split("/");
                String username = parts.length >= 2 ? parts[1] : "token";
                cmd.withAuthConfig(new AuthConfig()
                        .withRegistryAddress("https://ghcr.io")
                        .withUsername(username)
                        .withPassword(token));
            }
        }
        try {
            cmd.exec(new ResultCallback.Adapter<PullResponseItem>() {})
                    .awaitCompletion(300, TimeUnit.SECONDS);
            log.info("이미지 pull 완료: {}", image);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("이미지 pull 중단: " + image, e);
        }
    }

    private String resolveGhcrToken(String imageWithRef) {
        if (imageRegistryRepository == null) return null;
        String imageOnly = stripReference(imageWithRef);
        return imageRegistryRepository.findByImage(imageOnly)
                .map(r -> r.getGhcrToken())
                .filter(t -> t != null && !t.isBlank())
                .orElse(null);
    }

    private String stripReference(String ref) {
        // image@sha256:... 또는 image:tag → image
        int at = ref.indexOf('@');
        if (at >= 0) return ref.substring(0, at);
        int colon = ref.lastIndexOf(':');
        return colon >= 0 ? ref.substring(0, colon) : ref;
    }

    private String createContainer(DockerClient client, String name, String image,
                                    InspectContainerResponse inspect) {
        HostConfig hostConfig = inspect.getHostConfig() != null
                ? inspect.getHostConfig()
                : HostConfig.newHostConfig();

        String[] env = inspect.getConfig().getEnv() != null
                ? inspect.getConfig().getEnv()
                : new String[]{};

        CreateContainerResponse response = client.createContainerCmd(image)
                .withName(name)
                .withHostConfig(hostConfig)
                .withEnv(env)
                .withLabels(inspect.getConfig().getLabels() != null
                        ? inspect.getConfig().getLabels()
                        : java.util.Map.of())
                .exec();

        return response.getId();
    }

    private DockerClient resolveClient(String nodeId) {
        if (nodeId == null) return dockerClient;
        return nodeRegistry.findById(nodeId)
                .map(nodeClientFactory::createClient)
                .orElse(dockerClient);
    }

    private String parseName(String rawName) {
        if (rawName == null) return "unknown";
        return rawName.startsWith("/") ? rawName.substring(1) : rawName;
    }
}
