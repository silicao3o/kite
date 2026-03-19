package com.lite_k8s.metrics;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContainerStats {
    private String containerName;
    private double avgCpu;
    private double maxCpu;
    private double minCpu;
    private double avgMemory;
    private double maxMemory;
    private double minMemory;
    private int sampleCount;
}
