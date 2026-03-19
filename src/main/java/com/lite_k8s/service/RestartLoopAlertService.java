package com.lite_k8s.service;

import com.lite_k8s.config.MonitorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestartLoopAlertService {

    private final EmailNotificationService emailNotificationService;
    private final AlertDeduplicationService deduplicationService;
    private final RestartTracker restartTracker;
    private final MonitorProperties monitorProperties;

    public void checkAndAlert(String containerId, String containerName) {
        MonitorProperties.RestartLoop config = monitorProperties.getRestartLoop();

        if (!config.isEnabled()) {
            return;
        }

        int restartCount = restartTracker.getRestartCount(containerId);

        if (restartCount >= config.getThresholdCount()) {
            if (deduplicationService.shouldAlert(containerId, "RESTART_LOOP")) {
                log.warn("재시작 반복 감지: {} ({}분 내 {}회)",
                        containerName, config.getWindowMinutes(), restartCount);
                emailNotificationService.sendRestartLoopAlert(
                        containerName, containerId, restartCount, config.getWindowMinutes());
            }
        }
    }
}
