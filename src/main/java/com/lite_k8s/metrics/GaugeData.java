package com.lite_k8s.metrics;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GaugeData {
    private String containerId;
    private String containerName;
    private String nodeName;
    private double cpuPercent;
    private int cpuCount;
    private double memoryPercent;
    private long memoryUsage;       // bytes
    private long memoryLimit;       // bytes
    private long networkRxBytes;
    private long networkTxBytes;
}
