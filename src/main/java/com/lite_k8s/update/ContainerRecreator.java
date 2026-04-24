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
            String fullImage = buildImageRef(imageRef, inspect);

            log.info("컨테이너 업데이트 시작: {} ({})", containerName, newDigest);

            // 2. 새 이미지 pull — 로컬 :latest 가 옛날 이미지면 recreate 가 구버전으로 복제되는 걸 방지
            pullImage(client, fullImage);

            // 3. 기존 컨테이너 중지
            ownActionTracker.markOwnAction(containerId);
            client.stopContainerCmd(containerId).exec();
            log.debug("컨테이너 중지 완료: {}", containerName);

            // 4. 기존 컨테이너 제거
            client.removeContainerCmd(containerId).exec();
            log.debug("컨테이너 제거 완료: {}", containerName);

            // 5. 새 이미지로 컨테이너 생성
            String newContainerId = createContainer(client, containerName, fullImage, inspect);
            log.debug("컨테이너 생성 완료: {} → {}", containerName, newContainerId);

            // 6. 새 컨테이너 시작
            client.startContainerCmd(newContainerId).exec();
            log.info("컨테이너 업데이트 완료: {} → {}", containerName, newContainerId);

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

    private String resolveGhcrToken(String imageWithTag) {
        if (imageRegistryRepository == null) return null;
        String imageOnly = imageWithTag.contains(":")
                ? imageWithTag.substring(0, imageWithTag.lastIndexOf(":"))
                : imageWithTag;
        return imageRegistryRepository.findByImage(imageOnly)
                .map(r -> r.getGhcrToken())
                .filter(t -> t != null && !t.isBlank())
                .orElse(null);
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

    private String buildImageRef(String imageRef, InspectContainerResponse inspect) {
        // 이미지 레퍼런스에 태그가 없으면 latest 붙임
        if (!imageRef.contains(":")) {
            return imageRef + ":latest";
        }
        return imageRef;
    }
}
