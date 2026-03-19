package com.lite_k8s.metrics;

import com.lite_k8s.service.MetricsScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CurrentMetricsGaugeService {

    private final MetricsScheduler metricsScheduler;

    public Optional<GaugeData> getGauge(String containerId) {
        return metricsScheduler.getLatestMetrics(containerId)
                .map(m -> GaugeData.builder()
                        .containerId(m.getContainerId())
                        .containerName(m.getContainerName())
                        .cpuPercent(m.getCpuPercent())
                        .memoryPercent(m.getMemoryPercent())
                        .build());
    }

    public List<GaugeData> getAllGauges() {
        return metricsScheduler.getAllCachedMetrics().values().stream()
                .map(m -> GaugeData.builder()
                        .containerId(m.getContainerId())
                        .containerName(m.getContainerName())
                        .cpuPercent(m.getCpuPercent())
                        .memoryPercent(m.getMemoryPercent())
                        .build())
                .collect(Collectors.toList());
    }
}
