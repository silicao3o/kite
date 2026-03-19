package com.lite_k8s.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetricsAggregationService {

    private final MetricsHistoryService historyService;

    public List<HourlyAggregate> getHourlyAverage(String containerName, LocalDateTime from, LocalDateTime to) {
        List<MetricsSnapshot> snapshots = historyService.getHistoryByRange(containerName, from, to);
        return aggregateByHour(snapshots, false);
    }

    public List<HourlyAggregate> getHourlyMax(String containerName, LocalDateTime from, LocalDateTime to) {
        List<MetricsSnapshot> snapshots = historyService.getHistoryByRange(containerName, from, to);
        return aggregateByHour(snapshots, true);
    }

    public ContainerStats getContainerStats(String containerName, int hours) {
        List<MetricsSnapshot> snapshots = historyService.getHistory(containerName, hours);
        if (snapshots.isEmpty()) {
            return ContainerStats.builder().containerName(containerName).build();
        }
        double avgCpu = snapshots.stream().mapToDouble(MetricsSnapshot::getCpuPercent).average().orElse(0);
        double maxCpu = snapshots.stream().mapToDouble(MetricsSnapshot::getCpuPercent).max().orElse(0);
        double minCpu = snapshots.stream().mapToDouble(MetricsSnapshot::getCpuPercent).min().orElse(0);
        double avgMem = snapshots.stream().mapToDouble(MetricsSnapshot::getMemoryPercent).average().orElse(0);
        double maxMem = snapshots.stream().mapToDouble(MetricsSnapshot::getMemoryPercent).max().orElse(0);
        double minMem = snapshots.stream().mapToDouble(MetricsSnapshot::getMemoryPercent).min().orElse(0);
        return ContainerStats.builder()
                .containerName(containerName)
                .avgCpu(avgCpu).maxCpu(maxCpu).minCpu(minCpu)
                .avgMemory(avgMem).maxMemory(maxMem).minMemory(minMem)
                .sampleCount(snapshots.size())
                .build();
    }

    private List<HourlyAggregate> aggregateByHour(List<MetricsSnapshot> snapshots, boolean maxOnly) {
        Map<LocalDateTime, List<MetricsSnapshot>> byHour = snapshots.stream()
                .collect(Collectors.groupingBy(s -> s.getCollectedAt()
                        .withMinute(0).withSecond(0).withNano(0)));

        return byHour.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    List<MetricsSnapshot> group = e.getValue();
                    double avgCpu = group.stream().mapToDouble(MetricsSnapshot::getCpuPercent).average().orElse(0);
                    double maxCpu = group.stream().mapToDouble(MetricsSnapshot::getCpuPercent).max().orElse(0);
                    double avgMem = group.stream().mapToDouble(MetricsSnapshot::getMemoryPercent).average().orElse(0);
                    double maxMem = group.stream().mapToDouble(MetricsSnapshot::getMemoryPercent).max().orElse(0);
                    return HourlyAggregate.builder()
                            .hour(e.getKey())
                            .avgCpu(avgCpu).maxCpu(maxCpu)
                            .avgMemory(avgMem).maxMemory(maxMem)
                            .sampleCount(group.size())
                            .build();
                })
                .collect(Collectors.toList());
    }
}
