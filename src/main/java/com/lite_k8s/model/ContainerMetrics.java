package com.lite_k8s.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ContainerMetrics {
    private String containerId;
    private String containerName;

    // CPU
    private double cpuPercent;

    // Memory
    private long memoryUsage;      // bytes
    private long memoryLimit;      // bytes
    private double memoryPercent;

    // Network I/O
    private long networkRxBytes;   // received
    private long networkTxBytes;   // transmitted

    // Block I/O
    private long blockRead;
    private long blockWrite;

    private LocalDateTime collectedAt;
}
