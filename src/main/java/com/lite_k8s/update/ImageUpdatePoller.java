package com.lite_k8s.update;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * GHCR 이미지 digest 변경을 와치별 독립 주기로 폴링.
 * 각 와치마다 자체 pollIntervalSeconds에 따라 스케줄링된다.
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

    private TaskScheduler taskScheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

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

    @PostConstruct
    void init() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("image-poller-");
        scheduler.initialize();
        this.taskScheduler = scheduler;

        if (properties.isEnabled()) {
            scheduleAllWatches();
        }
    }

    @PreDestroy
    void destroy() {
        cancelAllSchedules();
        if (taskScheduler instanceof ThreadPoolTaskScheduler pool) {
            pool.shutdown();
        }
    }

    /** 애플리케이션 시작 시 모든 활성 와치 스케줄 등록 */
    void scheduleAllWatches() {
        List<ImageWatchEntity> watches = watchService.findEnabled();
        log.info("이미지 와치 스케줄 등록: {}개", watches.size());
        for (ImageWatchEntity watch : watches) {
            scheduleWatch(watch);
        }
    }

    /** 특정 와치의 스케줄을 등록/재등록한다 */
    public void scheduleWatch(ImageWatchEntity watch) {
        cancelSchedule(watch.getId());

        if (!watch.isEnabled() || !properties.isEnabled()) return;

        int intervalSeconds = Math.max(10, watch.getPollIntervalSeconds() != null ? watch.getPollIntervalSeconds() : 300);
        ScheduledFuture<?> future = taskScheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        checkWatch(watch.getId());
                    } catch (Exception e) {
                        log.error("이미지 감시 오류: {}", watch.getImage(), e);
                    }
                },
                Duration.ofSeconds(intervalSeconds)
        );
        scheduledTasks.put(watch.getId(), future);
        log.debug("와치 스케줄 등록: {} ({}초)", watch.getImage(), intervalSeconds);
    }

    /** 특정 와치의 스케줄을 해제한다 */
    public void cancelSchedule(String watchId) {
        ScheduledFuture<?> existing = scheduledTasks.remove(watchId);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    /** 모든 스케줄을 해제한다 */
    void cancelAllSchedules() {
        scheduledTasks.values().forEach(f -> f.cancel(false));
        scheduledTasks.clear();
    }

    /** 특정 와치를 ID로 즉시 체크 (트리거용) */
    public void checkWatch(String watchId) {
        watchService.findById(watchId).ifPresent(this::checkWatch);
    }

    /** 모든 활성 와치를 즉시 체크 (트리거용) */
    public void triggerAll() {
        List<ImageWatchEntity> watches = watchService.findEnabled();
        log.info("전체 와치 트리거: {}개", watches.size());
        for (ImageWatchEntity watch : watches) {
            try {
                checkWatch(watch);
            } catch (Exception e) {
                log.error("이미지 감시 오류: {}", watch.getImage(), e);
            }
        }
    }

    void checkWatch(ImageWatchEntity watch) {
        String latestDigest = ghcrClient.getLatestDigest(watch.getImage(), watch.getTag(), watch.getGhcrToken());
        if (latestDigest == null) {
            log.warn("GHCR digest 조회 실패: {}:{}", watch.getImage(), watch.getTag());
            return;
        }

        List<Node> nodes = nodeRegistry != null ? nodeRegistry.findAll() : List.of();
        List<String> targetNodeNames = watch.getNodeNames() != null ? watch.getNodeNames() : List.of();

        if (!nodes.isEmpty()) {
            for (Node node : nodes) {
                if (!targetNodeNames.isEmpty() && !targetNodeNames.contains(node.getName())) {
                    continue;
                }
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

    // 테스트용: TaskScheduler 주입
    void setTaskScheduler(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }
}
