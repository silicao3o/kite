package com.lite_k8s.metrics;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HourlyAggregate {
    private LocalDateTime hour;
    private double avgCpu;
    private double maxCpu;
    private double avgMemory;
    private double maxMemory;
    private int sampleCount;
}
