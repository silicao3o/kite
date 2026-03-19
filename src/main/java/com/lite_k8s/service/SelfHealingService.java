package com.lite_k8s.service;

import com.lite_k8s.config.SelfHealingProperties;
import com.lite_k8s.model.ContainerDeathEvent;
import com.lite_k8s.model.HealingEvent;
import com.lite_k8s.repository.HealingEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SelfHealingService {

    private final SelfHealingProperties properties;
    private final HealingRuleMatcher ruleMatcher;
    private final RestartTracker restartTracker;
    private final DockerService dockerService;
    private final ContainerLabelReader labelReader;
    private final HealingEventRepository healingEventRepository;
    private final EmailNotificationService emailNotificationService;
    private final RestartLoopAlertService restartLoopAlertService;

    public void handleContainerDeath(ContainerDeathEvent event) {
        if (!properties.isEnabled()) {
            return;
        }

        // 1. 라벨에서 설정 읽기 (우선)
        var ruleOpt = labelReader.readHealingConfig(event.getLabels());

        // 2. 라벨에 없으면 yml 규칙 사용
        if (ruleOpt.isEmpty()) {
            ruleOpt = ruleMatcher.findMatchingRule(event.getContainerName());
        }

        if (ruleOpt.isEmpty()) {
            return;
        }

        SelfHealingProperties.Rule rule = ruleOpt.get();
        String containerId = event.getContainerId();

        if (restartTracker.isMaxRestartsExceeded(containerId, rule.getMaxRestarts())) {
            log.warn("최대 재시작 횟수 초과: {} (max: {})",
                    event.getContainerName(), rule.getMaxRestarts());
            emailNotificationService.sendMaxRestartsExceededAlert(
                    event.getContainerName(), containerId, rule.getMaxRestarts());
            return;
        }

        log.info("자가치유 시작: {} (규칙: {})",
                event.getContainerName(), rule.getNamePattern());

        // 지연 시간 적용
        if (rule.getRestartDelaySeconds() > 0) {
            try {
                log.info("{}초 후 재시작 예정: {}", rule.getRestartDelaySeconds(), event.getContainerName());
                Thread.sleep(rule.getRestartDelaySeconds() * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("재시작 지연 중 인터럽트 발생: {}", event.getContainerName());
            }
        }

        boolean success = dockerService.restartContainer(containerId);
        if (success) {
            restartTracker.recordRestart(containerId);
            log.info("자가치유 완료: {}", event.getContainerName());
            saveHealingEvent(event, true, "자가치유 성공");
            // 재시작 반복 알림 체크
            restartLoopAlertService.checkAndAlert(containerId, event.getContainerName());
        } else {
            log.error("자가치유 실패: {}", event.getContainerName());
            saveHealingEvent(event, false, "자가치유 실패");
            emailNotificationService.sendRestartFailedAlert(event.getContainerName(), containerId);
        }
    }

    private void saveHealingEvent(ContainerDeathEvent event, boolean success, String message) {
        HealingEvent healingEvent = HealingEvent.builder()
                .containerId(event.getContainerId())
                .containerName(event.getContainerName())
                .timestamp(LocalDateTime.now())
                .success(success)
                .restartCount(restartTracker.getRestartCount(event.getContainerId()))
                .message(message)
                .build();
        healingEventRepository.save(healingEvent);
    }
}
