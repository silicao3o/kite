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
    private final DockerService dockerService;

    public void checkAndHandle(String containerId, String containerName, String nodeId) {
        MonitorProperties.RestartLoop config = monitorProperties.getRestartLoop();

        if (!config.isEnabled()) {
            return;
        }

        int restartCount = restartTracker.getRestartCount(containerId);

        if (restartCount >= config.getThresholdCount()) {
            if (deduplicationService.shouldAlert(containerId, "CRASH_LOOP")) {
                log.warn("Crash Loop 감지 — 컨테이너 강제 정지: {} ({}분 내 {}회)",
                        containerName, config.getWindowMinutes(), restartCount);
                dockerService.stopContainer(containerId, nodeId);
                emailNotificationService.sendCrashLoopStoppedAlert(
                        containerName, containerId, restartCount, config.getWindowMinutes());
            }
        }
    }
}
