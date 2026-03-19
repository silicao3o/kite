package com.lite_k8s.listener;

import com.lite_k8s.analyzer.ExitCodeAnalyzer;
import com.lite_k8s.config.MonitorProperties;
import com.lite_k8s.model.ContainerDeathEvent;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import com.lite_k8s.service.AlertDeduplicationService;
import com.lite_k8s.service.ContainerFilterService;
import com.lite_k8s.service.DockerService;
import com.lite_k8s.service.EmailNotificationService;
import com.lite_k8s.service.SelfHealingService;
import com.lite_k8s.incident.IncidentReportService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Event;
import com.github.dockerjava.api.model.EventType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class DockerEventListener {

    private final DockerClient dockerClient;
    private final DockerService dockerService;
    private final ExitCodeAnalyzer exitCodeAnalyzer;
    private final EmailNotificationService notificationService;
    private final MonitorProperties monitorProperties;
    private final ContainerFilterService containerFilterService;
    private final AlertDeduplicationService deduplicationService;
    private final SelfHealingService selfHealingService;
    private final IncidentReportService incidentReportService;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;

    private Closeable eventStream; // 로컬 단일 모드 스트림
    private final Map<String, Closeable> nodeStreams = new ConcurrentHashMap<>(); // 노드별 스트림
    private final AtomicInteger retryCount = new AtomicInteger(0);

    @Autowired
    public DockerEventListener(
            DockerClient dockerClient,
            DockerService dockerService,
            ExitCodeAnalyzer exitCodeAnalyzer,
            EmailNotificationService notificationService,
            MonitorProperties monitorProperties,
            ContainerFilterService containerFilterService,
            AlertDeduplicationService deduplicationService,
            SelfHealingService selfHealingService,
            IncidentReportService incidentReportService,
            NodeRegistry nodeRegistry,
            NodeDockerClientFactory nodeClientFactory) {
        this.dockerClient = dockerClient;
        this.dockerService = dockerService;
        this.exitCodeAnalyzer = exitCodeAnalyzer;
        this.notificationService = notificationService;
        this.monitorProperties = monitorProperties;
        this.containerFilterService = containerFilterService;
        this.deduplicationService = deduplicationService;
        this.selfHealingService = selfHealingService;
        this.incidentReportService = incidentReportService;
        this.nodeRegistry = nodeRegistry;
        this.nodeClientFactory = nodeClientFactory;
    }

    // 기존 테스트 호환 생성자 (nodeRegistry=null → 로컬 단일 모드)
    DockerEventListener(
            DockerClient dockerClient,
            DockerService dockerService,
            ExitCodeAnalyzer exitCodeAnalyzer,
            EmailNotificationService notificationService,
            MonitorProperties monitorProperties,
            ContainerFilterService containerFilterService,
            AlertDeduplicationService deduplicationService,
            SelfHealingService selfHealingService,
            IncidentReportService incidentReportService) {
        this(dockerClient, dockerService, exitCodeAnalyzer, notificationService, monitorProperties,
                containerFilterService, deduplicationService, selfHealingService, incidentReportService,
                null, null);
    }

    // 모니터링할 이벤트 액션들
    // kill은 제외: kill 후 반드시 die가 따라오므로 die에서만 처리 (중복 트리거 방지)
    private static final Set<String> DEATH_ACTIONS = Set.of("die", "oom");

    @PostConstruct
    public void startListening() {
        log.info("Docker 이벤트 리스너 시작...");

        List<Node> nodes = nodeRegistry != null ? nodeRegistry.findAll() : List.of();

        if (!nodes.isEmpty()) {
            // 멀티 노드 모드: 노드별 이벤트 스트림 병렬 생성
            for (Node node : nodes) {
                DockerClient client = nodeClientFactory.createClient(node);
                Closeable stream = client.eventsCmd()
                        .withEventTypeFilter(EventType.CONTAINER)
                        .exec(new ResultCallback.Adapter<Event>() {
                            @Override
                            public void onNext(Event event) {
                                handleEvent(event, node.getId());
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                log.error("노드 이벤트 스트림 에러: {}", node.getName(), throwable);
                            }
                        });
                if (stream != null) nodeStreams.put(node.getId(), stream);
                log.info("노드 이벤트 리스너 시작: {} ({}:{})", node.getName(), node.getHost(), node.getPort());
            }
        } else {
            // 로컬 단일 모드 (기존 동작)
            eventStream = dockerClient.eventsCmd()
                    .withEventTypeFilter(EventType.CONTAINER)
                    .exec(new ResultCallback.Adapter<Event>() {
                        @Override
                        public void onNext(Event event) {
                            handleEvent(event, null);
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            log.error("Docker 이벤트 스트림 에러 발생", throwable);
                            reconnect();
                        }
                    });
        }

        log.info("Docker 이벤트 리스너 시작 완료");
    }

    private void handleEvent(Event event, String nodeId) {
        String action = event.getAction();
        String containerId = event.getId();

        log.debug("Docker 이벤트 수신: action={}, containerId={}", action, containerId);

        // die, kill, oom 이벤트만 처리
        if (action != null && DEATH_ACTIONS.contains(action.toLowerCase())) {
            log.info("컨테이너 종료 감지: containerId={}, action={}", containerId, action);

            // 중복 알림 체크
            if (!deduplicationService.shouldAlert(containerId, action)) {
                log.info("중복 알림 스킵: containerId={}, action={}", containerId, action);
                return;
            }

            try {
                // 컨테이너 정보 수집
                ContainerDeathEvent deathEvent = dockerService.buildDeathEvent(containerId, action);
                deathEvent.setNodeId(nodeId);

                // 필터링 체크
                if (!containerFilterService.shouldMonitor(deathEvent.getContainerName(), deathEvent.getImageName())) {
                    log.info("컨테이너 필터링으로 알림 제외: {}", deathEvent.getContainerName());
                    return;
                }

                // Exit Code 분석하여 종료 원인 설정
                String deathReason = exitCodeAnalyzer.analyze(deathEvent);
                deathEvent.setDeathReason(deathReason);

                // 이메일 알림 전송
                notificationService.sendAlert(deathEvent);

                // 자가치유 시도
                selfHealingService.handleContainerDeath(deathEvent);

                // AI 사후 분석 리포트 생성 (비동기)
                incidentReportService.createReport(deathEvent);

                log.info("컨테이너 종료 알림 전송 완료: {}", deathEvent.getContainerName());

            } catch (Exception e) {
                log.error("컨테이너 종료 이벤트 처리 실패: {}", containerId, e);
            }
        }
    }

    private void reconnect() {
        MonitorProperties.Reconnect config = monitorProperties.getReconnect();
        int currentRetry = retryCount.incrementAndGet();
        int maxRetries = config.getMaxRetries();

        // 최대 재시도 횟수 초과 체크 (0이면 무제한)
        if (maxRetries > 0 && currentRetry > maxRetries) {
            log.error("최대 재시도 횟수({}) 초과. Docker 이벤트 리스너 중단.", maxRetries);
            log.error("수동으로 서비스를 재시작하거나 Docker 데몬 상태를 확인하세요.");
            return;
        }

        // 지수 백오프 계산: initialDelay * (multiplier ^ (retry-1))
        long delay = (long) (config.getInitialDelayMs() * Math.pow(config.getMultiplier(), currentRetry - 1));
        delay = Math.min(delay, config.getMaxDelayMs()); // 최대 대기 시간 제한

        log.info("Docker 이벤트 스트림 재연결 시도 {}/{}, {}초 후...",
                currentRetry,
                maxRetries > 0 ? maxRetries : "무제한",
                delay / 1000);

        try {
            Thread.sleep(delay);
            startListening();
            // 연결 성공 시 재시도 카운트 리셋
            retryCount.set(0);
            log.info("Docker 이벤트 스트림 재연결 성공");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("재연결 중 인터럽트 발생", e);
        }
    }

    @PreDestroy
    public void stopListening() {
        log.info("Docker 이벤트 리스너 종료...");
        if (eventStream != null) {
            try {
                eventStream.close();
            } catch (IOException e) {
                log.error("이벤트 스트림 종료 실패", e);
            }
        }
        nodeStreams.forEach((nodeId, stream) -> {
            try {
                stream.close();
            } catch (IOException e) {
                log.error("노드 이벤트 스트림 종료 실패: {}", nodeId, e);
            }
        });
        nodeStreams.clear();
    }
}
