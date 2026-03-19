package com.lite_k8s.metrics;

import com.lite_k8s.model.ContainerMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsHistoryService {

    private final MetricsHistoryRepository repository;

    public void record(ContainerMetrics metrics) {
        MetricsSnapshot snapshot = MetricsSnapshot.builder()
                .containerId(metrics.getContainerId())
                .containerName(metrics.getContainerName())
                .cpuPercent(metrics.getCpuPercent())
                .memoryPercent(metrics.getMemoryPercent())
                .memoryUsage(metrics.getMemoryUsage())
                .memoryLimit(metrics.getMemoryLimit())
                .networkRxBytes(metrics.getNetworkRxBytes())
                .networkTxBytes(metrics.getNetworkTxBytes())
                .collectedAt(metrics.getCollectedAt() != null ? metrics.getCollectedAt() : LocalDateTime.now())
                .build();

        repository.save(snapshot);
    }

    /**
     * @param hours 조회할 시간 범위 (예: 24 = 최근 24시간, 168 = 최근 7일)
     */
    public List<MetricsSnapshot> getHistory(String containerName, int hours) {
        LocalDateTime from = LocalDateTime.now().minusHours(hours);
        return repository.findByContainerName(containerName, from);
    }

    public List<MetricsSnapshot> getHistoryByRange(String containerName, LocalDateTime from, LocalDateTime to) {
        return repository.findByContainerNameAndRange(containerName, from, to);
    }

    public List<String> getAllContainerNames() {
        return repository.findAllContainerNames();
    }
}
