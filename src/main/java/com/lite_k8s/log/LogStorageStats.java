package com.lite_k8s.log;

import lombok.Builder;
import lombok.Data;

/**
 * 로그 저장소 통계
 */
@Data
@Builder
public class LogStorageStats {

    private long totalLogs;
    private int containerCount;
    private int retentionDays;
}
