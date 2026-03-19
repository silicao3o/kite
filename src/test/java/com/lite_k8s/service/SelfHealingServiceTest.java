package com.lite_k8s.service;

import com.lite_k8s.config.SelfHealingProperties;
import com.lite_k8s.model.ContainerDeathEvent;
import com.lite_k8s.repository.HealingEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SelfHealingServiceTest {

    @Mock
    private SelfHealingProperties properties;

    @Mock
    private HealingRuleMatcher ruleMatcher;

    @Mock
    private RestartTracker restartTracker;

    @Mock
    private DockerService dockerService;

    @Mock
    private ContainerLabelReader labelReader;

    @Mock
    private HealingEventRepository healingEventRepository;

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private RestartLoopAlertService restartLoopAlertService;

    private SelfHealingService selfHealingService;

    @BeforeEach
    void setUp() {
        selfHealingService = new SelfHealingService(
                properties, ruleMatcher, restartTracker, dockerService, labelReader,
                healingEventRepository, emailNotificationService, restartLoopAlertService);
    }

    private ContainerDeathEvent createDeathEvent(String containerId, String containerName) {
        return ContainerDeathEvent.builder()
                .containerId(containerId)
                .containerName(containerName)
                .imageName("test:latest")
                .deathTime(LocalDateTime.now())
                .exitCode(1L)
                .action("die")
                .build();
    }

    @Test
    @DisplayName("자가치유가 비활성화되면 아무 동작도 하지 않는다")
    void shouldDoNothingWhenDisabled() {
        // given
        when(properties.isEnabled()).thenReturn(false);
        ContainerDeathEvent event = createDeathEvent("abc123", "web-server");

        // when
        selfHealingService.handleContainerDeath(event);

        // then
        verify(ruleMatcher, never()).findMatchingRule(anyString());
        verify(dockerService, never()).restartContainer(anyString());
    }

    @Test
    @DisplayName("일치하는 규칙이 없으면 재시작하지 않는다")
    void shouldNotRestartWhenNoRuleMatches() {
        // given
        when(properties.isEnabled()).thenReturn(true);
        when(ruleMatcher.findMatchingRule("db-server")).thenReturn(Optional.empty());
        ContainerDeathEvent event = createDeathEvent("abc123", "db-server");

        // when
        selfHealingService.handleContainerDeath(event);

        // then
        verify(ruleMatcher).findMatchingRule("db-server");
        verify(dockerService, never()).restartContainer(anyString());
    }

    @Test
    @DisplayName("규칙이 일치하면 컨테이너를 재시작한다")
    void shouldRestartWhenRuleMatches() {
        // given
        when(properties.isEnabled()).thenReturn(true);

        SelfHealingProperties.Rule rule = new SelfHealingProperties.Rule();
        rule.setNamePattern("web-*");
        rule.setMaxRestarts(3);
        rule.setRestartDelaySeconds(0);

        when(ruleMatcher.findMatchingRule("web-server")).thenReturn(Optional.of(rule));
        when(restartTracker.isMaxRestartsExceeded("abc123", 3)).thenReturn(false);
        when(dockerService.restartContainer("abc123")).thenReturn(true);

        ContainerDeathEvent event = createDeathEvent("abc123", "web-server");

        // when
        selfHealingService.handleContainerDeath(event);

        // then
        verify(dockerService).restartContainer("abc123");
        verify(restartTracker).recordRestart("abc123");
    }

    @Test
    @DisplayName("최대 재시작 횟수를 초과하면 재시작하지 않는다")
    void shouldNotRestartWhenMaxRestartsExceeded() {
        // given
        when(properties.isEnabled()).thenReturn(true);

        SelfHealingProperties.Rule rule = new SelfHealingProperties.Rule();
        rule.setNamePattern("web-*");
        rule.setMaxRestarts(3);

        when(ruleMatcher.findMatchingRule("web-server")).thenReturn(Optional.of(rule));
        when(restartTracker.isMaxRestartsExceeded("abc123", 3)).thenReturn(true);

        ContainerDeathEvent event = createDeathEvent("abc123", "web-server");

        // when
        selfHealingService.handleContainerDeath(event);

        // then
        verify(dockerService, never()).restartContainer(anyString());
    }

    @Test
    @DisplayName("재시작 실패 시 재시작 횟수를 기록하지 않는다")
    void shouldNotRecordRestartWhenFailed() {
        // given
        when(properties.isEnabled()).thenReturn(true);

        SelfHealingProperties.Rule rule = new SelfHealingProperties.Rule();
        rule.setNamePattern("web-*");
        rule.setMaxRestarts(3);

        when(ruleMatcher.findMatchingRule("web-server")).thenReturn(Optional.of(rule));
        when(restartTracker.isMaxRestartsExceeded("abc123", 3)).thenReturn(false);
        when(dockerService.restartContainer("abc123")).thenReturn(false);

        ContainerDeathEvent event = createDeathEvent("abc123", "web-server");

        // when
        selfHealingService.handleContainerDeath(event);

        // then
        verify(dockerService).restartContainer("abc123");
        verify(restartTracker, never()).recordRestart(anyString());
    }

    // === 라벨 우선 로직 테스트 ===

    @Test
    @DisplayName("라벨에 설정이 있으면 라벨 설정을 우선 사용한다")
    void shouldUseLabelConfigWhenPresent() {
        // given
        when(properties.isEnabled()).thenReturn(true);

        Map<String, String> labels = Map.of("self-healing.enabled", "true");
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerId("abc123")
                .containerName("web-server")
                .imageName("test:latest")
                .deathTime(LocalDateTime.now())
                .exitCode(1L)
                .action("die")
                .labels(labels)
                .build();

        SelfHealingProperties.Rule labelRule = new SelfHealingProperties.Rule();
        labelRule.setMaxRestarts(5);

        when(labelReader.readHealingConfig(labels)).thenReturn(Optional.of(labelRule));
        when(restartTracker.isMaxRestartsExceeded("abc123", 5)).thenReturn(false);
        when(dockerService.restartContainer("abc123")).thenReturn(true);

        // when
        selfHealingService.handleContainerDeath(event);

        // then
        verify(labelReader).readHealingConfig(labels);
        verify(ruleMatcher, never()).findMatchingRule(anyString()); // yml 규칙은 체크 안함
        verify(dockerService).restartContainer("abc123");
    }

    @Test
    @DisplayName("라벨에 설정이 없으면 yml 규칙을 사용한다")
    void shouldUseYmlRuleWhenNoLabelConfig() {
        // given
        when(properties.isEnabled()).thenReturn(true);

        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerId("abc123")
                .containerName("web-server")
                .imageName("test:latest")
                .deathTime(LocalDateTime.now())
                .exitCode(1L)
                .action("die")
                .labels(Map.of()) // 빈 라벨
                .build();

        when(labelReader.readHealingConfig(any())).thenReturn(Optional.empty());

        SelfHealingProperties.Rule ymlRule = new SelfHealingProperties.Rule();
        ymlRule.setMaxRestarts(3);

        when(ruleMatcher.findMatchingRule("web-server")).thenReturn(Optional.of(ymlRule));
        when(restartTracker.isMaxRestartsExceeded("abc123", 3)).thenReturn(false);
        when(dockerService.restartContainer("abc123")).thenReturn(true);

        // when
        selfHealingService.handleContainerDeath(event);

        // then
        verify(labelReader).readHealingConfig(any());
        verify(ruleMatcher).findMatchingRule("web-server"); // yml 규칙 체크
        verify(dockerService).restartContainer("abc123");
    }

    @Test
    @DisplayName("라벨에서 enabled=false면 자가치유하지 않는다 (yml 규칙도 무시)")
    void shouldNotHealWhenLabelDisabled() {
        // given
        when(properties.isEnabled()).thenReturn(true);

        Map<String, String> labels = Map.of("self-healing.enabled", "false");
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerId("abc123")
                .containerName("web-server")
                .imageName("test:latest")
                .deathTime(LocalDateTime.now())
                .exitCode(1L)
                .action("die")
                .labels(labels)
                .build();

        when(labelReader.readHealingConfig(labels)).thenReturn(Optional.empty());
        when(ruleMatcher.findMatchingRule("web-server")).thenReturn(Optional.empty());

        // when
        selfHealingService.handleContainerDeath(event);

        // then
        verify(dockerService, never()).restartContainer(anyString());
    }

    // === 이력 저장 테스트 ===

    @Test
    @DisplayName("재시작 성공 시 이력을 저장한다")
    void shouldSaveHealingEventOnSuccess() {
        // given
        when(properties.isEnabled()).thenReturn(true);

        SelfHealingProperties.Rule rule = new SelfHealingProperties.Rule();
        rule.setNamePattern("web-*");
        rule.setMaxRestarts(3);

        when(labelReader.readHealingConfig(any())).thenReturn(Optional.empty());
        when(ruleMatcher.findMatchingRule("web-server")).thenReturn(Optional.of(rule));
        when(restartTracker.isMaxRestartsExceeded("abc123", 3)).thenReturn(false);
        when(restartTracker.getRestartCount("abc123")).thenReturn(1);
        when(dockerService.restartContainer("abc123")).thenReturn(true);

        ContainerDeathEvent event = createDeathEvent("abc123", "web-server");

        // when
        selfHealingService.handleContainerDeath(event);

        // then
        verify(healingEventRepository).save(argThat(healingEvent ->
                healingEvent.getContainerId().equals("abc123") &&
                healingEvent.isSuccess() == true
        ));
    }

    @Test
    @DisplayName("재시작 실패 시 이력을 저장한다")
    void shouldSaveHealingEventOnFailure() {
        // given
        when(properties.isEnabled()).thenReturn(true);

        SelfHealingProperties.Rule rule = new SelfHealingProperties.Rule();
        rule.setNamePattern("web-*");
        rule.setMaxRestarts(3);

        when(labelReader.readHealingConfig(any())).thenReturn(Optional.empty());
        when(ruleMatcher.findMatchingRule("web-server")).thenReturn(Optional.of(rule));
        when(restartTracker.isMaxRestartsExceeded("abc123", 3)).thenReturn(false);
        when(dockerService.restartContainer("abc123")).thenReturn(false);

        ContainerDeathEvent event = createDeathEvent("abc123", "web-server");

        // when
        selfHealingService.handleContainerDeath(event);

        // then
        verify(healingEventRepository).save(argThat(healingEvent ->
                healingEvent.getContainerId().equals("abc123") &&
                healingEvent.isSuccess() == false
        ));
    }

    @Test
    @DisplayName("restartDelaySeconds가 0보다 크면 지연 후 재시작한다")
    void shouldDelayBeforeRestart() {
        // given
        when(properties.isEnabled()).thenReturn(true);

        SelfHealingProperties.Rule rule = new SelfHealingProperties.Rule();
        rule.setNamePattern("web-*");
        rule.setMaxRestarts(3);
        rule.setRestartDelaySeconds(1); // 1초 지연

        when(labelReader.readHealingConfig(any())).thenReturn(Optional.empty());
        when(ruleMatcher.findMatchingRule("web-server")).thenReturn(Optional.of(rule));
        when(restartTracker.isMaxRestartsExceeded("abc123", 3)).thenReturn(false);
        when(dockerService.restartContainer("abc123")).thenReturn(true);

        ContainerDeathEvent event = createDeathEvent("abc123", "web-server");

        // when
        long startTime = System.currentTimeMillis();
        selfHealingService.handleContainerDeath(event);
        long elapsed = System.currentTimeMillis() - startTime;

        // then
        verify(dockerService).restartContainer("abc123");
        // 최소 900ms 이상 걸렸는지 확인 (약간의 오차 허용)
        org.assertj.core.api.Assertions.assertThat(elapsed).isGreaterThanOrEqualTo(900);
    }

    // === 알림 테스트 ===

    @Test
    @DisplayName("최대 재시작 횟수 초과 시 알림을 전송한다")
    void shouldSendNotificationWhenMaxRestartsExceeded() {
        // given
        when(properties.isEnabled()).thenReturn(true);

        SelfHealingProperties.Rule rule = new SelfHealingProperties.Rule();
        rule.setNamePattern("web-*");
        rule.setMaxRestarts(3);

        when(labelReader.readHealingConfig(any())).thenReturn(Optional.empty());
        when(ruleMatcher.findMatchingRule("web-server")).thenReturn(Optional.of(rule));
        when(restartTracker.isMaxRestartsExceeded("abc123", 3)).thenReturn(true);

        ContainerDeathEvent event = createDeathEvent("abc123", "web-server");

        // when
        selfHealingService.handleContainerDeath(event);

        // then
        verify(emailNotificationService).sendMaxRestartsExceededAlert("web-server", "abc123", 3);
        verify(dockerService, never()).restartContainer(anyString());
    }

    @Test
    @DisplayName("재시작 실패 시 알림을 전송한다")
    void shouldSendNotificationWhenRestartFails() {
        // given
        when(properties.isEnabled()).thenReturn(true);

        SelfHealingProperties.Rule rule = new SelfHealingProperties.Rule();
        rule.setNamePattern("web-*");
        rule.setMaxRestarts(3);

        when(labelReader.readHealingConfig(any())).thenReturn(Optional.empty());
        when(ruleMatcher.findMatchingRule("web-server")).thenReturn(Optional.of(rule));
        when(restartTracker.isMaxRestartsExceeded("abc123", 3)).thenReturn(false);
        when(dockerService.restartContainer("abc123")).thenReturn(false);

        ContainerDeathEvent event = createDeathEvent("abc123", "web-server");

        // when
        selfHealingService.handleContainerDeath(event);

        // then
        verify(emailNotificationService).sendRestartFailedAlert("web-server", "abc123");
    }
}
