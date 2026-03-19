package com.lite_k8s.log;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 로그 정리 스케줄러
 *
 * 보존 기간 초과 로그를 주기적으로 삭제
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogCleanupScheduler {

    private final LogStorageService logStorageService;

    /**
     * 매일 새벽 2시에 만료 로그 정리
     */
    @Scheduled(cron = "${docker.monitor.log-storage.cleanup-cron:0 0 2 * * *}")
    public void cleanupExpiredLogs() {
        log.info("로그 정리 시작...");
        int deleted = logStorageService.cleanupExpiredLogs();
        log.info("로그 정리 완료: {}개 삭제", deleted);
    }
}
