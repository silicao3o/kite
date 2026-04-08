package com.lite_k8s.listener;

import com.lite_k8s.analyzer.ExitCodeAnalyzer;
import com.lite_k8s.config.MonitorProperties;
import com.lite_k8s.model.ContainerDeathEvent;
import com.lite_k8s.service.AlertDeduplicationService;
import com.lite_k8s.service.ContainerFilterService;
import com.lite_k8s.service.DockerService;
import com.lite_k8s.service.EmailNotificationService;
import com.lite_k8s.service.SelfHealingService;
import com.lite_k8s.incident.IncidentReportService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.EventsCmd;
import com.github.dockerjava.api.model.Event;
import com.github.dockerjava.api.model.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class DockerEventListenerTest {

    @Mock
    private DockerClient dockerClient;

    @Mock
    private DockerService dockerService;

    @Mock
    private ExitCodeAnalyzer exitCodeAnalyzer;

    @Mock
    private EmailNotificationService notificationService;

    @Mock
    private ContainerFilterService containerFilterService;

    @Mock
    private AlertDeduplicationService deduplicationService;

    @Mock
    private SelfHealingService selfHealingService;

    @Mock
    private IncidentReportService incidentReportService;

    @Mock
    private EventsCmd eventsCmd;

    @Captor
    private ArgumentCaptor<ResultCallback<Event>> callbackCaptor;

    private MonitorProperties monitorProperties;
    private DockerEventListener dockerEventListener;

    @BeforeEach
    void setUp() {
        monitorProperties = new MonitorProperties();
        dockerEventListener = new DockerEventListener(
                dockerClient,
                dockerService,
                exitCodeAnalyzer,
                notificationService,
                monitorProperties,
                containerFilterService,
                deduplicationService,
                selfHealingService,
                incidentReportService
        );

        // 기본적으로 모든 컨테이너 모니터링 허용 (lenient: 사용하지 않는 테스트에서도 에러 안남)
        lenient().when(containerFilterService.shouldMonitor(any(), any())).thenReturn(true);
        // 기본적으로 중복 아님
        lenient().when(deduplicationService.shouldAlert(any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("die 이벤트 발생 시 알림 전송")
    void handleEvent_WhenDieEvent_ShouldSendNotification() {
        // given
        setupEventsCmdMock();

        ContainerDeathEvent deathEvent = createTestDeathEvent();
        when(dockerService.buildDeathEvent(anyString(), anyString(), any())).thenReturn(deathEvent);
        when(exitCodeAnalyzer.analyze(any())).thenReturn("SIGKILL로 강제 종료됨");

        // when
        dockerEventListener.startListening();

        // 이벤트 콜백 캡처 및 실행
        verify(eventsCmd).exec(callbackCaptor.capture());
        ResultCallback<Event> callback = callbackCaptor.getValue();

        Event dieEvent = createDockerEvent("die", "container123");
        callback.onNext(dieEvent);

        // then
        verify(dockerService).buildDeathEvent(anyString(), anyString(), any());
        verify(exitCodeAnalyzer).analyze(deathEvent);
        verify(notificationService).sendAlert(deathEvent);
    }

    @Test
    @DisplayName("kill 이벤트는 무시됨 (die 이벤트가 뒤따르므로 중복 방지)")
    void handleEvent_WhenKillEvent_ShouldBeIgnored() {
        // given
        setupEventsCmdMock();

        // when
        dockerEventListener.startListening();
        verify(eventsCmd).exec(callbackCaptor.capture());
        ResultCallback<Event> callback = callbackCaptor.getValue();

        Event killEvent = createDockerEvent("kill", "container456");
        callback.onNext(killEvent);

        // then - kill 이벤트는 처리하지 않음
        verify(dockerService, never()).buildDeathEvent(anyString(), anyString(), any());
        verify(notificationService, never()).sendAlert(any());
    }

    @Test
    @DisplayName("oom 이벤트 발생 시 알림 전송")
    void handleEvent_WhenOomEvent_ShouldSendNotification() {
        // given
        setupEventsCmdMock();

        ContainerDeathEvent deathEvent = ContainerDeathEvent.builder()
                .containerId("container789")
                .containerName("memory-app")
                .exitCode(137L)
                .oomKilled(true)
                .action("oom")
                .deathTime(LocalDateTime.now())
                .build();

        when(dockerService.buildDeathEvent(anyString(), anyString(), any())).thenReturn(deathEvent);
        when(exitCodeAnalyzer.analyze(any())).thenReturn("OOM Killed");

        // when
        dockerEventListener.startListening();
        verify(eventsCmd).exec(callbackCaptor.capture());
        ResultCallback<Event> callback = callbackCaptor.getValue();

        Event oomEvent = createDockerEvent("oom", "container789");
        callback.onNext(oomEvent);

        // then
        verify(dockerService).buildDeathEvent(anyString(), anyString(), any());
        verify(notificationService).sendAlert(deathEvent);
    }

    @Test
    @DisplayName("start 이벤트는 무시")
    void handleEvent_WhenStartEvent_ShouldNotSendNotification() {
        // given
        setupEventsCmdMock();

        // when
        dockerEventListener.startListening();
        verify(eventsCmd).exec(callbackCaptor.capture());
        ResultCallback<Event> callback = callbackCaptor.getValue();

        Event startEvent = createDockerEvent("start", "container123");
        callback.onNext(startEvent);

        // then
        verify(dockerService, never()).buildDeathEvent(anyString(), anyString(), any());
        verify(notificationService, never()).sendAlert(any());
    }

    @Test
    @DisplayName("stop 이벤트는 무시 (die 이벤트만 처리)")
    void handleEvent_WhenStopEvent_ShouldNotSendNotification() {
        // given
        setupEventsCmdMock();

        // when
        dockerEventListener.startListening();
        verify(eventsCmd).exec(callbackCaptor.capture());
        ResultCallback<Event> callback = callbackCaptor.getValue();

        Event stopEvent = createDockerEvent("stop", "container123");
        callback.onNext(stopEvent);

        // then
        verify(dockerService, never()).buildDeathEvent(anyString(), anyString(), any());
        verify(notificationService, never()).sendAlert(any());
    }

    @Test
    @DisplayName("이벤트 처리 중 예외 발생 시 서비스 계속 동작")
    void handleEvent_WhenExceptionOccurs_ShouldContinue() {
        // given
        setupEventsCmdMock();

        when(dockerService.buildDeathEvent(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Docker API error"));

        // when
        dockerEventListener.startListening();
        verify(eventsCmd).exec(callbackCaptor.capture());
        ResultCallback<Event> callback = callbackCaptor.getValue();

        Event dieEvent = createDockerEvent("die", "container123");
        callback.onNext(dieEvent);

        // then - 예외가 발생해도 서비스가 중단되지 않음
        verify(dockerService).buildDeathEvent(anyString(), anyString(), any());
        verify(notificationService, never()).sendAlert(any());
    }

    @Test
    @DisplayName("deathReason이 이벤트에 설정됨")
    void handleEvent_ShouldSetDeathReason() {
        // given
        setupEventsCmdMock();

        ContainerDeathEvent deathEvent = createTestDeathEvent();
        String expectedReason = "[Exit Code: 137] SIGKILL - 강제 종료됨";

        when(dockerService.buildDeathEvent(anyString(), anyString(), any())).thenReturn(deathEvent);
        when(exitCodeAnalyzer.analyze(any())).thenReturn(expectedReason);

        // when
        dockerEventListener.startListening();
        verify(eventsCmd).exec(callbackCaptor.capture());
        ResultCallback<Event> callback = callbackCaptor.getValue();

        Event dieEvent = createDockerEvent("die", "container123");
        callback.onNext(dieEvent);

        // then
        assertThat(deathEvent.getDeathReason()).isEqualTo(expectedReason);
    }

    @Test
    @DisplayName("리스너 종료 시 이벤트 스트림 닫기")
    void stopListening_ShouldCloseEventStream() {
        // given
        setupEventsCmdMock();
        dockerEventListener.startListening();

        // when
        dockerEventListener.stopListening();

        // then - 예외 없이 정상 종료
    }

    @Test
    @DisplayName("필터링된 컨테이너는 알림 제외")
    void handleEvent_WhenContainerFiltered_ShouldNotSendNotification() {
        // given
        setupEventsCmdMock();

        ContainerDeathEvent deathEvent = createTestDeathEvent();
        when(dockerService.buildDeathEvent(anyString(), anyString(), any())).thenReturn(deathEvent);
        // 필터링으로 제외
        when(containerFilterService.shouldMonitor(any(), any())).thenReturn(false);

        // when
        dockerEventListener.startListening();
        verify(eventsCmd).exec(callbackCaptor.capture());
        ResultCallback<Event> callback = callbackCaptor.getValue();

        Event dieEvent = createDockerEvent("die", "container123");
        callback.onNext(dieEvent);

        // then
        verify(dockerService).buildDeathEvent(anyString(), anyString(), any());
        verify(notificationService, never()).sendAlert(any());
    }

    @Test
    @DisplayName("중복 알림은 이메일만 스킵 — buildDeathEvent와 SelfHealing은 실행됨")
    void handleEvent_WhenDuplicateAlert_ShouldNotSendNotification() {
        // given
        setupEventsCmdMock();
        // 중복으로 판정
        when(deduplicationService.shouldAlert(any(), any())).thenReturn(false);

        ContainerDeathEvent deathEvent = createTestDeathEvent();
        when(dockerService.buildDeathEvent(anyString(), anyString(), any())).thenReturn(deathEvent);
        lenient().when(exitCodeAnalyzer.analyze(any())).thenReturn("exit code");

        // when
        dockerEventListener.startListening();
        verify(eventsCmd).exec(callbackCaptor.capture());
        ResultCallback<Event> callback = callbackCaptor.getValue();

        Event dieEvent = createDockerEvent("die", "container123");
        callback.onNext(dieEvent);

        // then - 이메일 알림만 스킵
        verify(dockerService).buildDeathEvent(anyString(), anyString(), any());
        verify(notificationService, never()).sendAlert(any());
    }

    private void setupEventsCmdMock() {
        when(dockerClient.eventsCmd()).thenReturn(eventsCmd);
        when(eventsCmd.withEventTypeFilter(any(EventType.class))).thenReturn(eventsCmd);
        when(eventsCmd.exec(any())).thenReturn(null);
    }

    private Event createDockerEvent(String action, String containerId) {
        Event event = mock(Event.class);
        when(event.getAction()).thenReturn(action);
        when(event.getId()).thenReturn(containerId);
        return event;
    }

    private ContainerDeathEvent createTestDeathEvent() {
        return ContainerDeathEvent.builder()
                .containerId("container123")
                .containerName("test-container")
                .imageName("nginx:latest")
                .deathTime(LocalDateTime.now())
                .exitCode(137L)
                .oomKilled(false)
                .action("die")
                .lastLogs("Some logs here")
                .build();
    }

    @Test
    @DisplayName("중복 알림이더라도 SelfHealingService는 실행된다")
    void handleEvent_WhenDuplicateAlert_ShouldStillCallSelfHealingService() {
        // given
        setupEventsCmdMock();
        when(deduplicationService.shouldAlert(any(), any())).thenReturn(false);

        ContainerDeathEvent deathEvent = createTestDeathEvent();
        when(dockerService.buildDeathEvent(anyString(), anyString(), any())).thenReturn(deathEvent);
        lenient().when(exitCodeAnalyzer.analyze(any())).thenReturn("exit code");

        // when
        dockerEventListener.startListening();
        verify(eventsCmd).exec(callbackCaptor.capture());
        ResultCallback<Event> callback = callbackCaptor.getValue();

        Event dieEvent = createDockerEvent("die", "container123");
        callback.onNext(dieEvent);

        // then - SelfHealing은 dedup과 무관하게 실행
        verify(selfHealingService).handleContainerDeath(deathEvent);
        // 이메일 알림은 전송하지 않음
        verify(notificationService, never()).sendAlert(any());
    }

    @Test
    @DisplayName("die 이벤트 발생 시 SelfHealingService 호출")
    void handleEvent_WhenDieEvent_ShouldCallSelfHealingService() {
        // given
        setupEventsCmdMock();

        ContainerDeathEvent deathEvent = createTestDeathEvent();
        when(dockerService.buildDeathEvent(anyString(), anyString(), any())).thenReturn(deathEvent);
        when(exitCodeAnalyzer.analyze(any())).thenReturn("SIGKILL로 강제 종료됨");

        // when
        dockerEventListener.startListening();
        verify(eventsCmd).exec(callbackCaptor.capture());
        ResultCallback<Event> callback = callbackCaptor.getValue();

        Event dieEvent = createDockerEvent("die", "container123");
        callback.onNext(dieEvent);

        // then
        verify(selfHealingService).handleContainerDeath(deathEvent);
    }

    @Test
    @DisplayName("7.14: 자체 액션으로 인한 die 이벤트는 전체 플로우 스킵")
    void handleEvent_WhenOwnAction_ShouldSkipEverything() {
        // given
        setupEventsCmdMock();

        com.lite_k8s.service.OwnActionTracker ownActionTracker =
                mock(com.lite_k8s.service.OwnActionTracker.class);
        com.lite_k8s.service.IntentionalDeathClassifier classifier =
                new com.lite_k8s.service.IntentionalDeathClassifier();

        DockerEventListener listener = new DockerEventListener(
                dockerClient, dockerService, exitCodeAnalyzer, notificationService,
                monitorProperties, containerFilterService, deduplicationService,
                selfHealingService, incidentReportService, null, null,
                ownActionTracker, classifier);

        when(ownActionTracker.isOwnAction("container123")).thenReturn(true);

        // when
        listener.startListening();
        verify(eventsCmd).exec(callbackCaptor.capture());
        ResultCallback<Event> callback = callbackCaptor.getValue();

        Event dieEvent = createDockerEvent("die", "container123");
        callback.onNext(dieEvent);

        // then - 자체 액션이므로 buildDeathEvent조차 호출되지 않음
        verify(dockerService, never()).buildDeathEvent(anyString(), anyString(), any());
        verify(selfHealingService, never()).handleContainerDeath(any());
        verify(notificationService, never()).sendAlert(any());
        verify(incidentReportService, never()).createReport(any());
    }

    @Test
    @DisplayName("7.14: intentional(stop 이벤트 선행)으로 판정되면 self-heal과 알림 모두 스킵")
    void handleEvent_WhenIntentional_ShouldSkipHealAndAlert() {
        // given
        setupEventsCmdMock();

        ContainerDeathEvent deathEvent = ContainerDeathEvent.builder()
                .containerId("container123")
                .containerName("web-1")
                .exitCode(143L)
                .oomKilled(false)
                .action("die")
                .build();
        when(dockerService.buildDeathEvent(anyString(), anyString(), any())).thenReturn(deathEvent);
        lenient().when(exitCodeAnalyzer.analyze(any())).thenReturn("SIGTERM");

        // when — 먼저 stop 이벤트 수신 → pendingDeathCause에 저장
        dockerEventListener.startListening();
        verify(eventsCmd).exec(callbackCaptor.capture());
        ResultCallback<Event> callback = callbackCaptor.getValue();

        callback.onNext(createDockerEvent("stop", "container123"));
        callback.onNext(createDockerEvent("die", "container123"));

        // then - intentional 판정되어 self-heal / 알림 스킵
        verify(selfHealingService, never()).handleContainerDeath(any());
        verify(notificationService, never()).sendAlert(any());
        verify(incidentReportService).createReport(deathEvent);
        assertThat(deathEvent.isIntentional()).isTrue();
        assertThat(deathEvent.getIntentionalReason()).isEqualTo("stop-event-precedent");
    }

    @Test
    @DisplayName("7.14: intentional이 아니면 기존 플로우 그대로 (self-heal + 알림)")
    void handleEvent_WhenNotIntentional_ShouldProceedNormally() {
        // given
        setupEventsCmdMock();

        ContainerDeathEvent deathEvent = ContainerDeathEvent.builder()
                .containerId("container123")
                .containerName("web-1")
                .exitCode(1L)
                .oomKilled(false)
                .action("die")
                .build();
        when(dockerService.buildDeathEvent(anyString(), anyString(), any())).thenReturn(deathEvent);
        lenient().when(exitCodeAnalyzer.analyze(any())).thenReturn("app error");

        // when
        dockerEventListener.startListening();
        verify(eventsCmd).exec(callbackCaptor.capture());
        ResultCallback<Event> callback = callbackCaptor.getValue();

        callback.onNext(createDockerEvent("die", "container123"));

        // then
        verify(selfHealingService).handleContainerDeath(deathEvent);
        verify(notificationService).sendAlert(deathEvent);
        assertThat(deathEvent.isIntentional()).isFalse();
    }

    @Test
    @DisplayName("로컬 단일 모드에서 buildDeathEvent에 nodeId=null 전달")
    void handleEvent_LocalMode_ShouldPassNullNodeIdToBuildDeathEvent() {
        // given
        setupEventsCmdMock();

        ContainerDeathEvent deathEvent = createTestDeathEvent();
        when(dockerService.buildDeathEvent(anyString(), anyString(), any())).thenReturn(deathEvent);
        lenient().when(exitCodeAnalyzer.analyze(any())).thenReturn("exit");

        // when
        dockerEventListener.startListening();
        verify(eventsCmd).exec(callbackCaptor.capture());
        ResultCallback<Event> callback = callbackCaptor.getValue();

        Event dieEvent = createDockerEvent("die", "container123");
        callback.onNext(dieEvent);

        // then — 로컬 단일 모드이므로 nodeId=null
        verify(dockerService).buildDeathEvent("container123", "die", null);
    }
}
