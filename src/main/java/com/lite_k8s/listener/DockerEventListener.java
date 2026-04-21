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
import com.lite_k8s.service.IntentionalDeathClassifier;
import com.lite_k8s.service.OwnActionTracker;
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
    private final OwnActionTracker ownActionTracker;
    private final IntentionalDeathClassifier intentionalDeathClassifier;

    private Closeable eventStream; // 로컬 단일 모드 스트림
    private final Map<String, Closeable> nodeStreams = new ConcurrentHashMap<>(); // 노드별 스트림
    private final AtomicInteger retryCount = new AtomicInteger(0);

    // 선행 이벤트(kill, oom) 원인을 임시 저장 — die 이벤트에서 꺼내서 합침 (ContainerId, die_Reason)
    private final Map<String, String> pendingDeathCause = new ConcurrentHashMap<>();
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
            NodeDockerClientFactory nodeClientFactory,
            OwnActionTracker ownActionTracker,
            IntentionalDeathClassifier intentionalDeathClassifier) {
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
        this.ownActionTracker = ownActionTracker;
        this.intentionalDeathClassifier = intentionalDeathClassifier;
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
                null, null, new OwnActionTracker(), new IntentionalDeathClassifier());
    }

    // 멀티 노드 테스트용 생성자 (OwnActionTracker + Classifier는 실제 인스턴스 사용)
    DockerEventListener(
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
        this(dockerClient, dockerService, exitCodeAnalyzer, notificationService, monitorProperties,
                containerFilterService, deduplicationService, selfHealingService, incidentReportService,
                nodeRegistry, nodeClientFactory, new OwnActionTracker(), new IntentionalDeathClassifier());
    }

    // kill → die, oom → kill → die 순서로 이벤트가 발생하므로
    // 최종 이벤트인 die만 처리하여 중복 트리거를 방지한다.
    // exit code는 die 시점에 확정되며, OOM 여부는 inspect의 oomKilled 플래그로 판별한다.
    // stop 이벤트는 사용자의 docker stop 여부를 판별하기 위한 선행 신호로 기록한다.
    private static final Set<String> DEATH_ACTIONS = Set.of("die");
    private static final Set<String> PRE_DEATH_ACTIONS = Set.of("kill", "oom", "stop");

    @PostConstruct
    public void startListening() {
        log.info("Docker 이벤트 리스너 시작...");

        List<Node> nodes = nodeRegistry != null ? nodeRegistry.findAll() : List.of();

        // 로컬 이벤트 스트림은 항상 생성
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
        log.info("로컬 Docker 이벤트 리스너 시작");

        if (!nodes.isEmpty()) {
            // 멀티 노드 모드: 원격 노드 이벤트 스트림 추가 생성
            for (Node node : nodes) {
                DockerClient client;
                try {
                    client = nodeClientFactory.createClient(node);
                } catch (Exception e) {
                    log.warn("노드 이벤트 스트림 스킵 [{}] — 연결 실패: {}", node.getName(), e.getMessage());
                    continue;
                }
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
        }

        log.info("Docker 이벤트 리스너 시작 완료");
    }

    private void handleEvent(Event event, String nodeId) {
        String action = event.getAction();
        String containerId = event.getId() != null
                ? event.getId()
                : (event.getActor() != null ? event.getActor().getId() : null);

        log.debug("Docker 이벤트 수신: action={}, containerId={}", action, containerId);
        // 안전하게 null을 먼저 걸러내는 것이 중요 action이나 containerId가 null이면 이벤트 처리 자체가 불가능함
        if (action == null || containerId == null) return;

        if (PRE_DEATH_ACTIONS.contains(action.toLowerCase())) {
            // 선행 이벤트는 die 이벤트에서 원인으로 활용하기 위해 임시 저장
            pendingDeathCause.put(containerId, action.toLowerCase());
            log.debug("선행 이벤트 저장: containerId={}, action={}", containerId, action);
        }


        // die 이벤트만 처리하도록 수정
        if (action != null && DEATH_ACTIONS.contains(action.toLowerCase())) {
            log.info("컨테이너 종료 이벤트 감지: pendingDeathCause={}, action={}", pendingDeathCause.toString(), action);
            // 저장된 선행 원인 꺼내기 (꺼내면서 삭제)
            String previousCause = pendingDeathCause.remove(containerId);
            log.info("컨테이너 종료 감지: containerId={}, action={}", containerId, action);

            // 우리 자신의 API 호출로 인한 종료는 전체 플로우 스킵
            // (self-heal, recreate, reconcile 등이 방금 restart한 결과로 생긴 이벤트)
            if (ownActionTracker != null && ownActionTracker.isOwnAction(containerId)) {
                log.info("자체 액션으로 인한 종료 이벤트 — 플로우 스킵: containerId={}", containerId);
                return;
            }

            // ※ 1차 dedup 체크 제거: 자가치유는 dedup과 무관하게 항상 실행되어야 crash loop 감지가 정상 작동함
            //   dedup은 이메일 발송 직전에만 한 번 적용 (아래 sendAlert 부분)

            try {
                // 컨테이너 정보 수집 (nodeId로 올바른 클라이언트 선택 — 라벨 포함)
                ContainerDeathEvent deathEvent = dockerService.buildDeathEvent(containerId, action, nodeId);
                deathEvent.setNodeId(nodeId);

                // 선행 원인이 있으면 action을 덮어씌움 (die → kill 또는 oom)
                if (previousCause != null) {
                    deathEvent.setAction(previousCause);
                }
                // 필터링 체크
                if (!containerFilterService.shouldMonitor(deathEvent.getContainerName(), deathEvent.getImageName())) {
                    log.info("컨테이너 필터링으로 알림 제외: {}", deathEvent.getContainerName());
                    return;
                }

                // Exit Code 분석하여 종료 원인 설정
                String deathReason = exitCodeAnalyzer.analyze(deathEvent);
                deathEvent.setDeathReason(deathReason);

                // 의도적 종료 판정 (사용자의 docker stop/kill 등)
                if (intentionalDeathClassifier != null) {
                    intentionalDeathClassifier.classify(deathEvent);
                }

                // 자가치유: intentional이면 스킵 (사용자의 명시적 명령을 되돌리지 않음)
                // 알림: sendAlert 내부에서 규칙+intentional 게이트 (notifyIntentional 규칙으로 override 가능)
                if (deathEvent.isIntentional()) {
                    log.info("의도적 종료 — 자가치유 스킵: containerId={}, reason={}",
                            containerId, deathEvent.getIntentionalReason());
                } else {
                    // 자가치유 시도 — dedup과 무관하게 항상 실행 (RestartTracker가 crash loop 감지)
                    selfHealingService.handleContainerDeath(deathEvent);
                }

                // 이메일 알림 전송 — 의도적 종료면 스킵, dedup 체크 후 sendAlert에 위임
                if (!deathEvent.isIntentional() && deduplicationService.shouldAlert(containerId, action)) {
                    notificationService.sendAlert(deathEvent);
                    log.info("컨테이너 종료 알림 요청 완료: {}", deathEvent.getContainerName());
                } else if (deathEvent.isIntentional()) {
                    log.info("의도적 종료 — 이메일 알림 스킵: containerId={}", containerId);
                } else {
                    log.info("중복 알림 스킵 (이메일만): containerId={}, action={}", containerId, action);
                }

                // AI 사후 분석 리포트 생성 (비동기) — 의도적 종료면 스킵
                if (!deathEvent.isIntentional()) {
                    incidentReportService.createReport(deathEvent);
                }

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
