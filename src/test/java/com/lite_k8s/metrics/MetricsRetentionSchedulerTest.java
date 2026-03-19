package com.lite_k8s.metrics;

import com.lite_k8s.model.ContainerMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsRetentionSchedulerTest {

    private MetricsRetentionScheduler scheduler;
    private MetricsHistoryService historyService;
    private MetricsHistoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MetricsHistoryRepository();
        historyService = new MetricsHistoryService(repository);
        MetricsRetentionProperties properties = new MetricsRetentionProperties();
        properties.setRetentionDays(30);
        scheduler = new MetricsRetentionScheduler(repository, properties);
    }

    @Test
    @DisplayName("보존 기간(30일)이 지난 메트릭은 삭제된다")
    void shouldDeleteExpiredMetrics() {
        // given
        record("web", 50.0, 31); // 31일 전 - 삭제 대상
        record("web", 60.0, 20); // 20일 전 - 보존 대상
        record("web", 70.0, 1);  //  1일 전 - 보존 대상

        // when
        scheduler.deleteExpiredMetrics();

        // then
        List<MetricsSnapshot> history = historyService.getHistory("web", 30 * 24);
        assertThat(history).hasSize(2);
    }

    @Test
    @DisplayName("보존 기간 내 데이터는 삭제되지 않는다")
    void shouldKeepMetricsWithinRetentionPeriod() {
        // given
        record("web", 50.0, 5);
        record("web", 60.0, 10);

        // when
        scheduler.deleteExpiredMetrics();

        // then
        List<MetricsSnapshot> history = historyService.getHistory("web", 30 * 24);
        assertThat(history).hasSize(2);
    }

    @Test
    @DisplayName("보존 기간 설정값을 읽는다")
    void shouldUseConfiguredRetentionDays() {
        // given
        MetricsRetentionProperties props = new MetricsRetentionProperties();
        props.setRetentionDays(7);
        MetricsRetentionScheduler shortRetention = new MetricsRetentionScheduler(repository, props);

        record("web", 50.0, 8);  // 8일 전 - 삭제 대상
        record("web", 60.0, 5);  // 5일 전 - 보존 대상

        // when
        shortRetention.deleteExpiredMetrics();

        // then
        List<MetricsSnapshot> history = historyService.getHistory("web", 7 * 24);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getCpuPercent()).isEqualTo(60.0);
    }

    private void record(String name, double cpu, int daysAgo) {
        historyService.record(ContainerMetrics.builder()
                .containerId(name + "-id").containerName(name)
                .cpuPercent(cpu).memoryPercent(50.0)
                .collectedAt(LocalDateTime.now().minusDays(daysAgo)).build());
    }
}
