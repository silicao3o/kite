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

    /** 특정 와치의 스케줄을 등록/재등록한다 (POLLING 모드만) */
    public void scheduleWatch(ImageWatchEntity watch) {
        cancelSchedule(watch.getId());

        if (!watch.isEnabled() || !properties.isEnabled()) return;
        if (watch.getMode() != ImageWatchEntity.WatchMode.POLLING) return;

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
        String token = watch.getEffectiveGhcrToken();
        String effectiveImage = watch.getEffectiveImage();
        log.info("와치 체크 시작: {}:{} (pattern={}, targetNodes={})",
                effectiveImage, watch.getTag(), watch.getContainerPattern(),
                watch.getNodeNames() != null ? watch.getNodeNames() : List.of());

        String latestDigest = ghcrClient.getLatestDigest(effectiveImage, watch.getTag(), token);
        if (latestDigest == null) {
            log.warn("GHCR digest 조회 실패: {}:{}", watch.getImage(), watch.getTag());
            return;
        }
        log.info("GHCR 최신 digest: {}:{} → {}", effectiveImage, watch.getTag(), shorten(latestDigest));

        List<Node> nodes = nodeRegistry != null ? nodeRegistry.findAll() : List.of();
        List<String> targetNodeNames = watch.getNodeNames() != null ? watch.getNodeNames() : List.of();

        int totalMatched = 0;
        int totalUpdated = 0;
        if (!nodes.isEmpty()) {
            for (Node node : nodes) {
                if (!targetNodeNames.isEmpty() && !targetNodeNames.contains(node.getName())) {
                    log.debug("노드 스킵 (타겟 아님): {}", node.getName());
                    continue;
                }
                DockerClient client = nodeClientFactory.createClient(node);
                List<Container> containers = client.listContainersCmd().withShowAll(false).exec();
                int[] counts = checkContainers(containers, watch, latestDigest, node.getId());
                log.info("노드 {}: 매칭 {}개, 업데이트 대상 {}개", node.getName(), counts[0], counts[1]);
                totalMatched += counts[0];
                totalUpdated += counts[1];
            }
        } else {
            List<Container> containers = dockerClient.listContainersCmd().withShowAll(false).exec();
            int[] counts = checkContainers(containers, watch, latestDigest, null);
            log.info("로컬 노드: 매칭 {}개, 업데이트 대상 {}개", counts[0], counts[1]);
            totalMatched = counts[0];
            totalUpdated = counts[1];
        }

        if (totalMatched == 0) {
            log.info("와치 체크 완료: {}:{} — 매칭 컨테이너 0개 (pattern={})",
                    effectiveImage, watch.getTag(), watch.getContainerPattern());
        } else if (totalUpdated == 0) {
            log.info("와치 체크 완료: {}:{} — 변화 없음 (매칭 {}개 모두 최신)",
                    effectiveImage, watch.getTag(), totalMatched);
        } else {
            log.info("와치 체크 완료: {}:{} — 업데이트 감지 {}/{}",
                    effectiveImage, watch.getTag(), totalUpdated, totalMatched);
        }
    }

    /** @return [matchedCount, updatedCount] */
    private int[] checkContainers(List<Container> containers, ImageWatchEntity watch,
                                  String latestDigest, String nodeId) {
        int matched = 0;
        int updated = 0;
        String watchImage = watch.getEffectiveImage();
        for (Container container : containers) {
            String name = extractName(container);
            if (!matchesPattern(name, watch.getContainerPattern())) {
                continue;
            }
            // 이름은 매칭됐지만 실제 이미지 레포가 다르면 스킵 (sidecar 등)
            // 예: chat-quvi.* 패턴에 chat-quvi-nginx(nginx:alpine)가 걸리는 것 방지
            if (!imageRepoMatches(container.getImage(), watchImage)) {
                log.debug("이미지 레포 불일치로 스킵: {} (container image={}, watch image={})",
                        name, container.getImage(), watchImage);
                continue;
            }
            matched++;

            String currentDigest = container.getImageId();
            if (!latestDigest.equals(currentDigest)) {
                updated++;
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
        return new int[]{matched, updated};
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
        // glob 패턴이면 regex로 변환 (*, ? 포함 + 유효한 regex가 아닌 경우)
        String regex = isGlobPattern(pattern) ? globToRegex(pattern) : pattern;
        try {
            return name.matches(regex);
        } catch (java.util.regex.PatternSyntaxException e) {
            // 그래도 실패하면 substring 폴백
            log.warn("컨테이너 패턴 매칭 실패 — substring 폴백: pattern={} reason={}",
                    pattern, e.getDescription());
            String core = pattern.replace("*", "").replace("?", "");
            return !core.isEmpty() && name.contains(core);
        }
    }

    /** 컨테이너의 이미지 레포가 watch 이미지와 같은지 확인 (태그/digest 무시) */
    private boolean imageRepoMatches(String containerImage, String watchImage) {
        if (containerImage == null || watchImage == null) return false;
        return containerImage.equals(watchImage)
                || containerImage.startsWith(watchImage + ":")
                || containerImage.startsWith(watchImage + "@");
    }

    private boolean isGlobPattern(String pattern) {
        // glob 문자(*, ?)가 있고, 정규식 전용 문자(.*, .+, \d 등)가 없으면 glob으로 간주
        if (!pattern.contains("*") && !pattern.contains("?")) return false;
        return !pattern.contains(".*") && !pattern.contains(".+") && !pattern.contains("\\");
    }

    private String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                case '.' -> sb.append("\\.");
                default -> sb.append(c);
            }
        }
        return sb.toString();
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
