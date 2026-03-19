package com.lite_k8s.metrics;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GaugeData {
    private String containerId;
    private String containerName;
    private double cpuPercent;
    private double memoryPercent;
}
