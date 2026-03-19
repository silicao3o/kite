package com.lite_k8s.metrics;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MetricsSnapshot {
    private String containerId;
    private String containerName;
    private double cpuPercent;
    private double memoryPercent;
    private long memoryUsage;
    private long memoryLimit;
    private long networkRxBytes;
    private long networkTxBytes;
    private LocalDateTime collectedAt;
}
