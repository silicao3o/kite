package com.lite_k8s.metrics;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class HealingStatistics {
    private long totalCount;
    private long successCount;
    private long failureCount;
    private double successRate;
    private Map<String, Long> countPerContainer;
}
