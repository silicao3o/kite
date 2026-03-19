package com.lite_k8s.log;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogStorageServiceTest {

    private LogStorageService service;
    private LogStorageProperties properties;

    @BeforeEach
    void setUp() {
        properties = new LogStorageProperties();
        properties.setRetentionDays(7);
        properties.setMaxRetentionDays(30);
        service = new LogStorageService(properties);
    }

    @Test
    @DisplayName("로그 저장")
    void shouldStoreLog() {
        // given
        String containerId = "container-1";
        String logLine = "2024-01-01T10:00:00Z INFO Application started";

        // when
        service.store(containerId, logLine);

        // then
        List<StoredLog> logs = service.getLogs(containerId, null, null);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getContent()).isEqualTo(logLine);
    }

    @Test
    @DisplayName("컨테이너별 로그 조회")
    void shouldGetLogsByContainer() {
        // given
        service.store("container-1", "Log line 1");
        service.store("container-1", "Log line 2");
        service.store("container-2", "Log line 3");

        // when
        List<StoredLog> logs = service.getLogs("container-1", null, null);

        // then
        assertThat(logs).hasSize(2);
    }

    @Test
    @DisplayName("시간 범위 필터")
    void shouldFilterByTimeRange() {
        // given
        LocalDateTime now = LocalDateTime.now();
        service.storeWithTimestamp("container-1", "Old log", now.minusHours(2));
        service.storeWithTimestamp("container-1", "Recent log", now.minusMinutes(30));
        service.storeWithTimestamp("container-1", "New log", now);

        // when
        List<StoredLog> logs = service.getLogs("container-1", now.minusHours(1), null);

        // then
        assertThat(logs).hasSize(2);
    }

    @Test
    @DisplayName("보존 기간 초과 로그 삭제")
    void shouldDeleteExpiredLogs() {
        // given
        LocalDateTime now = LocalDateTime.now();
        service.storeWithTimestamp("container-1", "Old log", now.minusDays(10));
        service.storeWithTimestamp("container-1", "Recent log", now.minusDays(3));

        // when
        int deleted = service.cleanupExpiredLogs();

        // then
        assertThat(deleted).isEqualTo(1);
        List<StoredLog> logs = service.getLogs("container-1", null, null);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getContent()).isEqualTo("Recent log");
    }

    @Test
    @DisplayName("보존 기간 설정 변경")
    void shouldRespectRetentionDays() {
        // given
        properties.setRetentionDays(3);
        LocalDateTime now = LocalDateTime.now();
        service.storeWithTimestamp("container-1", "4 days old", now.minusDays(4));
        service.storeWithTimestamp("container-1", "2 days old", now.minusDays(2));

        // when
        int deleted = service.cleanupExpiredLogs();

        // then
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    @DisplayName("최대 보존 기간 제한")
    void shouldEnforceMaxRetentionDays() {
        // given
        properties.setRetentionDays(60);  // 30일 초과 요청

        // then
        assertThat(service.getEffectiveRetentionDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("컨테이너별 로그 수 제한")
    void shouldLimitLogsPerContainer() {
        // given
        properties.setMaxLogsPerContainer(5);
        for (int i = 0; i < 10; i++) {
            service.store("container-1", "Log " + i);
        }

        // when
        List<StoredLog> logs = service.getLogs("container-1", null, null);

        // then
        assertThat(logs.size()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("전체 로그 통계")
    void shouldGetStats() {
        // given
        service.store("container-1", "Log 1");
        service.store("container-1", "Log 2");
        service.store("container-2", "Log 3");

        // when
        LogStorageStats stats = service.getStats();

        // then
        assertThat(stats.getTotalLogs()).isEqualTo(3);
        assertThat(stats.getContainerCount()).isEqualTo(2);
    }
}
