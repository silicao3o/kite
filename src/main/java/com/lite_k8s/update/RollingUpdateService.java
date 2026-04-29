package com.lite_k8s.update;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import com.lite_k8s.util.DockerContainerNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolling Update 오케스트레이터
 *
 * - ImageUpdateDetectedEvent를 수신하여 업데이트 시작
 * - maxUnavailable 설정에 따라 순차/병렬 업데이트
 * - 업데이트 결과를 이력에 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RollingUpdateService {

    private final DockerClient dockerClient;
    private final ContainerRecreator recreator;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;
    private final ImageUpdateHistoryService historyService;

    @EventListener
    public void onImageUpdateDetected(ImageUpdateDetectedEvent event) {
        ImageWatchEntity watch = event.getWatch();
        log.info("Rolling Update 시작: {} ({}개 컨테이너)",
                event.getImageName(), "조회 중");

        List<Container> targets = findMatchingContainers(watch.getContainerPattern(), event.getNodeId());

        if (targets.isEmpty()) {
            log.warn("업데이트 대상 컨테이너 없음: 패턴={}", watch.getContainerPattern());
            return;
        }

        List<UpdateResult> results = executeRollingUpdate(targets, watch, event.getNewDigest(), event.getNodeId());

        long success = results.stream().filter(UpdateResult::isSuccess).count();
        long failed = results.size() - success;
        log.info("Rolling Update 완료: 성공={}, 실패={}", success, failed);
    }

    List<UpdateResult> executeRollingUpdate(
            List<Container> targets,
            ImageWatchEntity watch,
            String newDigest) {
        return executeRollingUpdate(targets, watch, newDigest, null);
    }

    List<UpdateResult> executeRollingUpdate(
            List<Container> targets,
            ImageWatchEntity watch,
            String newDigest,
            String nodeId) {

        List<UpdateResult> results = new ArrayList<>();
        int maxUnavailable = Math.max(1, watch.getMaxUnavailable());

        for (int i = 0; i < targets.size(); i += maxUnavailable) {
            int end = Math.min(i + maxUnavailable, targets.size());
            List<Container> batch = targets.subList(i, end);

            for (Container container : batch) {
                String name = extractName(container);
                String oldDigest = container.getImageId();

                log.info("[{}/{}] 업데이트 중: {}", i + 1, targets.size(), name);

                String pullRef = buildPullRef(watch, newDigest);
                boolean ok = recreator.recreate(container.getId(), pullRef, newDigest, nodeId);

                if (ok) {
                    results.add(UpdateResult.success(container.getId(), name, oldDigest, newDigest));
                    recordHistory(watch, name, oldDigest, newDigest,
                            ImageUpdateHistoryEntity.Status.SUCCESS, null);
                } else {
                    String errorMsg = "재생성 실패";
                    log.error("업데이트 실패: {} → 이후 컨테이너는 유지", name);
                    results.add(UpdateResult.failure(container.getId(), name, oldDigest, newDigest, errorMsg));
                    recordHistory(watch, name, oldDigest, newDigest,
                            ImageUpdateHistoryEntity.Status.FAILED, errorMsg);
                }
            }
        }

        return results;
    }

    private void recordHistory(ImageWatchEntity watch, String containerName,
                                String oldDigest, String newDigest,
                                ImageUpdateHistoryEntity.Status status, String message) {
        try {
            historyService.record(ImageUpdateHistoryEntity.builder()
                    .watchId(watch.getId())
                    .image(watch.getImage())
                    .tag(watch.getTag())
                    .previousDigest(oldDigest)
                    .newDigest(newDigest)
                    .status(status)
                    .containerName(containerName)
                    .message(message)
                    .build());
        } catch (Exception e) {
            log.error("이력 저장 실패: {}", containerName, e);
        }
    }

    private List<Container> findMatchingContainers(String pattern, String nodeId) {
        DockerClient client = resolveClient(nodeId);
        return client.listContainersCmd()
                .withShowAll(false)
                .exec()
                .stream()
                .filter(c -> matchesPattern(extractName(c), pattern))
                .toList();
    }

    private DockerClient resolveClient(String nodeId) {
        if (nodeId == null) return dockerClient;
        return nodeRegistry.findById(nodeId)
                .map(nodeClientFactory::createClient)
                .orElse(dockerClient);
    }

    private String extractName(Container container) {
        return DockerContainerNames.extractName(container);
    }

    private boolean matchesPattern(String name, String pattern) {
        if (pattern == null || pattern.isEmpty()) return true;
        return name.matches(pattern);
    }

    private String buildPullRef(ImageWatchEntity watch, String newDigest) {
        // digest로 pin해서 폴러가 본 그 이미지 그대로 pull — 태그가 도중에 이동해도 안전
        String image = watch.getEffectiveImage();
        if (newDigest != null && newDigest.startsWith("sha256:")) {
            return image + "@" + newDigest;
        }
        String tag = watch.getTag() != null && !watch.getTag().isBlank() ? watch.getTag() : "latest";
        return image + ":" + tag;
    }
}
