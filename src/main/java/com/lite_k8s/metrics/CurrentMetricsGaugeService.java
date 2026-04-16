package com.lite_k8s.metrics;

import com.lite_k8s.model.ContainerInfo;
import com.lite_k8s.model.ContainerMetrics;
import com.lite_k8s.service.MetricsScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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
                        .cpuCount(m.getCpuCount())
                        .memoryPercent(m.getMemoryPercent())
                        .memoryUsage(m.getMemoryUsage())
                        .memoryLimit(m.getMemoryLimit())
                        .networkRxBytes(m.getNetworkRxBytes())
                        .networkTxBytes(m.getNetworkTxBytes())
                        .build());
    }

    public List<GaugeData> getAllGauges() {
        Map<String, ContainerInfo> containerMap = metricsScheduler.getCachedContainers().stream()
                .collect(Collectors.toMap(ContainerInfo::getId, Function.identity(), (a, b) -> a));

        return metricsScheduler.getAllCachedMetrics().values().stream()
                .map(m -> {
                    ContainerInfo info = containerMap.get(m.getContainerId());
                    String nodeName = info != null && info.getNodeName() != null ? info.getNodeName() : "local";
                    return GaugeData.builder()
                            .containerId(m.getContainerId())
                            .containerName(m.getContainerName())
                            .nodeName(nodeName)
                            .cpuPercent(m.getCpuPercent())
                            .cpuCount(m.getCpuCount())
                            .memoryPercent(m.getMemoryPercent())
                            .memoryUsage(m.getMemoryUsage())
                            .memoryLimit(m.getMemoryLimit())
                            .networkRxBytes(m.getNetworkRxBytes())
                            .networkTxBytes(m.getNetworkTxBytes())
                            .build();
                })
                .collect(Collectors.toList());
    }
}
