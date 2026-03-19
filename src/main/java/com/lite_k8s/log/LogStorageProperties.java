package com.lite_k8s.log;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 로그 보존 설정
 */
@Data
@Component
@ConfigurationProperties(prefix = "docker.monitor.log-storage")
public class LogStorageProperties {

    /**
     * 로그 보존 기간 (일)
     */
    private int retentionDays = 7;

    /**
     * 최대 보존 기간 (일)
     */
    private int maxRetentionDays = 30;

    /**
     * 컨테이너당 최대 로그 수
     */
    private int maxLogsPerContainer = 10000;

    /**
     * 정리 스케줄 (cron 표현식)
     */
    private String cleanupCron = "0 0 2 * * *";  // 매일 새벽 2시
}
