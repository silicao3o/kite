package com.lite_k8s.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 감사 로그 보존 정책 스케줄러
 *
 * 보존 기간이 지난 로그를 자동으로 삭제
 * 기본 보존 기간: 180일
 */
@Slf4j
@Component
public class AuditLogRetentionScheduler {

    private final AuditLogRepository repository;
    private final int retentionDays;

    public AuditLogRetentionScheduler(
            AuditLogRepository repository,
            @Value("${docker.monitor.audit.retention-days:180}") int retentionDays) {
        this.repository = repository;
        this.retentionDays = retentionDays;
    }

    /**
     * 매일 자정에 만료된 로그 삭제
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void cleanupExpiredLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = repository.deleteOlderThan(cutoff);

        if (deleted > 0) {
            log.info("Deleted {} audit logs older than {} days", deleted, retentionDays);
        }
    }
}
