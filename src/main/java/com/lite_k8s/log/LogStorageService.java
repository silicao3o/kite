package com.lite_k8s.log;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 로그 저장 서비스
 *
 * 컨테이너 로그를 메모리에 저장하고 보존 정책 적용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogStorageService {

    private final LogStorageProperties properties;

    private final Map<String, List<StoredLog>> logsByContainer = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    /**
     * 로그 저장
     */
    public void store(String containerId, String content) {
        storeWithTimestamp(containerId, content, LocalDateTime.now());
    }

    /**
     * 타임스탬프와 함께 로그 저장
     */
    public void storeWithTimestamp(String containerId, String content, LocalDateTime timestamp) {
        StoredLog storedLog = StoredLog.builder()
                .id(String.valueOf(idGenerator.incrementAndGet()))
                .containerId(containerId)
                .content(content)
                .timestamp(timestamp)
                .storedAt(LocalDateTime.now())
                .build();

        logsByContainer.computeIfAbsent(containerId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(storedLog);

        // 컨테이너당 최대 로그 수 제한 적용
        enforceMaxLogsPerContainer(containerId);
    }

    /**
     * 컨테이너별 로그 조회
     */
    public List<StoredLog> getLogs(String containerId, LocalDateTime fromTime, LocalDateTime toTime) {
        List<StoredLog> logs = logsByContainer.getOrDefault(containerId, Collections.emptyList());

        return logs.stream()
                .filter(log -> fromTime == null || !log.getTimestamp().isBefore(fromTime))
                .filter(log -> toTime == null || !log.getTimestamp().isAfter(toTime))
                .sorted(Comparator.comparing(StoredLog::getTimestamp))
                .collect(Collectors.toList());
    }

    /**
     * 보존 기간 초과 로그 삭제
     */
    public int cleanupExpiredLogs() {
        int effectiveRetentionDays = getEffectiveRetentionDays();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(effectiveRetentionDays);

        int totalDeleted = 0;

        for (Map.Entry<String, List<StoredLog>> entry : logsByContainer.entrySet()) {
            List<StoredLog> logs = entry.getValue();
            int before = logs.size();

            logs.removeIf(log -> log.getTimestamp().isBefore(cutoff));

            int deleted = before - logs.size();
            totalDeleted += deleted;

            if (deleted > 0) {
                log.debug("컨테이너 {} 만료 로그 삭제: {}개", entry.getKey(), deleted);
            }
        }

        if (totalDeleted > 0) {
            log.info("만료 로그 정리 완료: {}개 삭제 (보존 기간: {}일)", totalDeleted, effectiveRetentionDays);
        }

        return totalDeleted;
    }

    /**
     * 실제 적용되는 보존 기간 (최대값 제한 적용)
     */
    public int getEffectiveRetentionDays() {
        return Math.min(properties.getRetentionDays(), properties.getMaxRetentionDays());
    }

    /**
     * 로그 통계
     */
    public LogStorageStats getStats() {
        long totalLogs = logsByContainer.values().stream()
                .mapToLong(List::size)
                .sum();

        return LogStorageStats.builder()
                .totalLogs(totalLogs)
                .containerCount(logsByContainer.size())
                .retentionDays(getEffectiveRetentionDays())
                .build();
    }

    private void enforceMaxLogsPerContainer(String containerId) {
        List<StoredLog> logs = logsByContainer.get(containerId);
        if (logs == null) return;

        int maxLogs = properties.getMaxLogsPerContainer();
        while (logs.size() > maxLogs) {
            logs.remove(0);  // 가장 오래된 로그 삭제
        }
    }

    /**
     * 특정 컨테이너의 모든 로그 삭제
     */
    public void clearLogs(String containerId) {
        logsByContainer.remove(containerId);
    }

    /**
     * 모든 로그 삭제
     */
    public void clearAllLogs() {
        logsByContainer.clear();
    }
}
