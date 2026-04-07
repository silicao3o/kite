package com.lite_k8s.update;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.lite_k8s.node.Node;
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
 * - 업데이트 실패 시 실패 기록 (롤백은 ContainerRecreator가 처리)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RollingUpdateService {

    private final DockerClient dockerClient;
    private final ContainerRecreator recreator;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;

    @EventListener
    public void onImageUpdateDetected(ImageUpdateDetectedEvent event) {
        ImageWatchProperties.ImageWatch watch = event.getWatch();
        log.info("Rolling Update 시작: {} ({}개 컨테이너)",
                event.getImageName(), "조회 중");

        // 패턴과 일치하는 모든 실행 중 컨테이너 수집
        List<Container> targets = findMatchingContainers(watch.getContainerPattern(), event.getNodeId());

        if (targets.isEmpty()) {
            log.warn("업데이트 대상 컨테이너 없음: 패턴={}", watch.getContainerPattern());
            return;
        }

        List<UpdateResult> results = executeRollingUpdate(targets, watch, event.getNewDigest());

        long success = results.stream().filter(UpdateResult::isSuccess).count();
        long failed = results.size() - success;
        log.info("Rolling Update 완료: 성공={}, 실패={}", success, failed);
    }

    /**
     * Rolling Update 실행
     * maxUnavailable 만큼씩 배치로 업데이트
     */
    List<UpdateResult> executeRollingUpdate(
            List<Container> targets,
            ImageWatchProperties.ImageWatch watch,
            String newDigest) {

        List<UpdateResult> results = new ArrayList<>();
        int maxUnavailable = Math.max(1, watch.getMaxUnavailable());

        // maxUnavailable 단위로 배치 처리
        for (int i = 0; i < targets.size(); i += maxUnavailable) {
            int end = Math.min(i + maxUnavailable, targets.size());
            List<Container> batch = targets.subList(i, end);

            for (Container container : batch) {
                String name = extractName(container);
                String oldDigest = container.getImageId();

                log.info("[{}/{}] 업데이트 중: {}", i + 1, targets.size(), name);

                boolean ok = recreator.recreate(container.getId(), watch.getImage(), newDigest);

                if (ok) {
                    results.add(UpdateResult.success(container.getId(), name, oldDigest, newDigest));
                } else {
                    log.error("업데이트 실패: {} → 이후 컨테이너는 유지", name);
                    results.add(UpdateResult.failure(
                            container.getId(), name, oldDigest, newDigest, "재생성 실패"));
                }
            }
        }

        return results;
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
}
