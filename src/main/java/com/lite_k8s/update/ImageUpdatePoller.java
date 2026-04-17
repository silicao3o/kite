package com.lite_k8s.update;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * GHCR 이미지 digest 변경을 주기적으로 폴링
 * DB에서 활성 와치를 조회하여 새 버전 감지 시 ImageUpdateDetectedEvent 발행
 */
@Slf4j
@Component
public class ImageUpdatePoller {

    private final ImageWatchProperties properties;
    private final ImageWatchService watchService;
    private final GhcrClient ghcrClient;
    private final DockerClient dockerClient;
    private final ApplicationEventPublisher eventPublisher;
    private final ImageUpdateHistoryService historyService;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;

    @Autowired
    public ImageUpdatePoller(
            ImageWatchProperties properties,
            ImageWatchService watchService,
            GhcrClient ghcrClient,
            DockerClient dockerClient,
            ApplicationEventPublisher eventPublisher,
            ImageUpdateHistoryService historyService,
            NodeRegistry nodeRegistry,
            NodeDockerClientFactory nodeClientFactory) {
        this.properties = properties;
        this.watchService = watchService;
        this.ghcrClient = ghcrClient;
        this.dockerClient = dockerClient;
        this.eventPublisher = eventPublisher;
        this.historyService = historyService;
        this.nodeRegistry = nodeRegistry;
        this.nodeClientFactory = nodeClientFactory;
    }

    // 테스트 호환 생성자
    ImageUpdatePoller(
            ImageWatchProperties properties,
            ImageWatchService watchService,
            GhcrClient ghcrClient,
            DockerClient dockerClient,
            ApplicationEventPublisher eventPublisher,
            ImageUpdateHistoryService historyService) {
        this(properties, watchService, ghcrClient, dockerClient, eventPublisher, historyService, null, null);
    }

    @Scheduled(fixedDelayString = "#{${docker.monitor.image-watch.poll-interval-seconds:300} * 1000}")
    public void pollAll() {
        if (!properties.isEnabled()) {
            return;
        }

        List<ImageWatchEntity> watches = watchService.findEnabled();
        log.debug("이미지 업데이트 폴링 시작: {}개 감시 중", watches.size());
        for (ImageWatchEntity watch : watches) {
            try {
                checkWatch(watch);
            } catch (Exception e) {
                log.error("이미지 감시 오류: {}", watch.getImage(), e);
            }
        }
    }

    void checkWatch(ImageWatchEntity watch) {
        String latestDigest = ghcrClient.getLatestDigest(watch.getImage(), watch.getTag());
        if (latestDigest == null) {
            log.warn("GHCR digest 조회 실패: {}:{}", watch.getImage(), watch.getTag());
            return;
        }

        List<Node> nodes = nodeRegistry != null ? nodeRegistry.findAll() : List.of();

        if (!nodes.isEmpty()) {
            for (Node node : nodes) {
                DockerClient client = nodeClientFactory.createClient(node);
                List<Container> containers = client.listContainersCmd().withShowAll(false).exec();
                checkContainers(containers, watch, latestDigest, node.getId());
            }
        } else {
            List<Container> containers = dockerClient.listContainersCmd().withShowAll(false).exec();
            checkContainers(containers, watch, latestDigest, null);
        }
    }

    private void checkContainers(List<Container> containers, ImageWatchEntity watch,
                                  String latestDigest, String nodeId) {
        for (Container container : containers) {
            String name = extractName(container);
            if (!matchesPattern(name, watch.getContainerPattern())) {
                continue;
            }

            String currentDigest = container.getImageId();
            if (!latestDigest.equals(currentDigest)) {
                log.info("새 이미지 감지: {} ({} → {})", name,
                        shorten(currentDigest), shorten(latestDigest));

                // DETECTED 이력 저장
                historyService.record(ImageUpdateHistoryEntity.builder()
                        .watchId(watch.getId())
                        .image(watch.getImage())
                        .tag(watch.getTag())
                        .previousDigest(currentDigest)
                        .newDigest(latestDigest)
                        .status(ImageUpdateHistoryEntity.Status.DETECTED)
                        .containerName(name)
                        .build());

                eventPublisher.publishEvent(new ImageUpdateDetectedEvent(
                        container.getId(),
                        name,
                        watch.getImage(),
                        watch.getTag(),
                        currentDigest,
                        latestDigest,
                        watch,
                        nodeId
                ));
            }
        }
    }

    private String extractName(Container container) {
        if (container.getNames() == null || container.getNames().length == 0) {
            return "";
        }
        String name = container.getNames()[0];
        return name.startsWith("/") ? name.substring(1) : name;
    }

    private boolean matchesPattern(String name, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return true;
        }
        return name.matches(pattern);
    }

    private String shorten(String digest) {
        if (digest == null) return "null";
        return digest.length() > 19 ? digest.substring(0, 19) + "..." : digest;
    }
}
