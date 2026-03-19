package com.lite_k8s.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsDailyCompressor {

    private final MetricsHistoryRepository repository;
    private final MetricsHistoryService historyService;

    public void compressDay(String containerName, LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = from.plusDays(1);

        List<MetricsSnapshot> snapshots = historyService.getHistoryByRange(containerName, from, to);
        if (snapshots.isEmpty()) {
            return;
        }

        double avgCpu = snapshots.stream().mapToDouble(MetricsSnapshot::getCpuPercent).average().orElse(0);
        double avgMem = snapshots.stream().mapToDouble(MetricsSnapshot::getMemoryPercent).average().orElse(0);
        long avgMemUsage = (long) snapshots.stream().mapToLong(MetricsSnapshot::getMemoryUsage).average().orElse(0);
        long avgMemLimit = (long) snapshots.stream().mapToLong(MetricsSnapshot::getMemoryLimit).average().orElse(0);

        MetricsSnapshot compressed = MetricsSnapshot.builder()
                .containerId(snapshots.get(0).getContainerId())
                .containerName(containerName)
                .cpuPercent(avgCpu)
                .memoryPercent(avgMem)
                .memoryUsage(avgMemUsage)
                .memoryLimit(avgMemLimit)
                .collectedAt(from)
                .build();

        // 기존 데이터 제거 후 압축본 저장
        repository.deleteOlderThan(to);
        repository.save(compressed);

        log.info("메트릭 일별 압축 완료: {} / {} ({}개 → 1개)", containerName, date, snapshots.size());
    }
}
