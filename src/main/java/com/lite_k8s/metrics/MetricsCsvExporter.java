package com.lite_k8s.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MetricsCsvExporter {

    private static final String HEADER = "timestamp,container,cpu_percent,memory_percent,memory_usage_mb,memory_limit_mb,network_rx_bytes,network_tx_bytes\n";

    private final MetricsHistoryService metricsHistoryService;

    public String exportMetrics(String containerName, int hours) {
        List<MetricsSnapshot> history = metricsHistoryService.getHistory(containerName, hours);

        StringBuilder sb = new StringBuilder(HEADER);
        for (MetricsSnapshot s : history) {
            sb.append(s.getCollectedAt()).append(",")
              .append(s.getContainerName()).append(",")
              .append(String.format("%.1f", s.getCpuPercent())).append(",")
              .append(String.format("%.1f", s.getMemoryPercent())).append(",")
              .append(s.getMemoryUsage() / (1024 * 1024)).append(",")
              .append(s.getMemoryLimit() / (1024 * 1024)).append(",")
              .append(s.getNetworkRxBytes()).append(",")
              .append(s.getNetworkTxBytes()).append("\n");
        }
        return sb.toString();
    }
}
