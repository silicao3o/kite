package com.lite_k8s.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsRetentionScheduler {

    private final MetricsHistoryRepository repository;
    private final MetricsRetentionProperties properties;

    @Scheduled(cron = "0 0 3 * * *")
    public void deleteExpiredMetrics() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(properties.getRetentionDays());
        repository.deleteOlderThan(cutoff);
        log.info("만료 메트릭 삭제 완료 (보존 기간: {}일, 기준: {})", properties.getRetentionDays(), cutoff);
    }
}
