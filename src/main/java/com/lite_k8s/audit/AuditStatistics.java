package com.lite_k8s.audit;

import lombok.Builder;
import lombok.Getter;

/**
 * 감사 로그 통계
 */
@Getter
@Builder
public class AuditStatistics {

    private long totalCount;
    private long successCount;
    private long failureCount;
    private long blockedCount;
    private long timeoutCount;
    private long pendingCount;
    private double successRate;

    public static AuditStatistics empty() {
        return AuditStatistics.builder()
                .totalCount(0)
                .successCount(0)
                .failureCount(0)
                .blockedCount(0)
                .timeoutCount(0)
                .pendingCount(0)
                .successRate(0.0)
                .build();
    }
}
