package com.lite_k8s.service;

import com.lite_k8s.config.MonitorProperties;
import com.lite_k8s.model.ContainerMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThresholdAlertService {

    private final EmailNotificationService emailNotificationService;
    private final AlertDeduplicationService deduplicationService;
    private final MonitorProperties monitorProperties;

    public void checkAndAlert(ContainerMetrics metrics) {
        if (!monitorProperties.getMetrics().isThresholdAlertEnabled()) {
            return;
        }

        checkCpuThreshold(metrics);
        checkMemoryThreshold(metrics);
    }

    private void checkCpuThreshold(ContainerMetrics metrics) {
        double threshold = monitorProperties.getMetrics().getCpuThresholdPercent();
        if (metrics.getCpuPercent() > threshold) {
            if (deduplicationService.shouldAlert(metrics.getContainerId(), "CPU_THRESHOLD")) {
                log.warn("CPU 임계치 초과: {} ({}% > {}%)",
                        metrics.getContainerName(),
                        String.format("%.1f", metrics.getCpuPercent()),
                        threshold);
                emailNotificationService.sendCpuThresholdAlert(
                        metrics.getContainerName(),
                        metrics.getContainerId(),
                        metrics.getCpuPercent(),
                        threshold);
            }
        }
    }

    private void checkMemoryThreshold(ContainerMetrics metrics) {
        double threshold = monitorProperties.getMetrics().getMemoryThresholdPercent();
        if (metrics.getMemoryPercent() > threshold) {
            if (deduplicationService.shouldAlert(metrics.getContainerId(), "MEMORY_THRESHOLD")) {
                log.warn("메모리 임계치 초과: {} ({}% > {}%)",
                        metrics.getContainerName(),
                        String.format("%.1f", metrics.getMemoryPercent()),
                        threshold);
                emailNotificationService.sendMemoryThresholdAlert(
                        metrics.getContainerName(),
                        metrics.getContainerId(),
                        metrics.getMemoryPercent(),
                        threshold);
            }
        }
    }
}
