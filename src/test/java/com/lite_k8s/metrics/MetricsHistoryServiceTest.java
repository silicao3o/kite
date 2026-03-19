package com.lite_k8s.metrics;

import com.lite_k8s.model.ContainerMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsHistoryServiceTest {

    private MetricsHistoryService service;

    @BeforeEach
    void setUp() {
        service = new MetricsHistoryService(new MetricsHistoryRepository());
    }

    @Test
    @DisplayName("메트릭을 기록하면 저장된다")
    void shouldRecordMetrics() {
        // given
        ContainerMetrics metrics = buildMetrics("web-server", 45.0, 70.0, LocalDateTime.now());

        // when
        service.record(metrics);

        // then
        List<MetricsSnapshot> history = service.getHistory("web-server", 24);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getContainerName()).isEqualTo("web-server");
    }

    @Test
    @DisplayName("24시간 기준으로 메트릭 이력을 조회한다")
    void shouldGetLast24HoursHistory() {
        // given
        service.record(buildMetrics("web-server", 30.0, 60.0, LocalDateTime.now().minusHours(25))); // 범위 밖
        service.record(buildMetrics("web-server", 50.0, 75.0, LocalDateTime.now().minusHours(10)));
        service.record(buildMetrics("web-server", 70.0, 80.0, LocalDateTime.now().minusHours(1)));

        // when
        List<MetricsSnapshot> history = service.getHistory("web-server", 24);

        // then
        assertThat(history).hasSize(2);
    }

    @Test
    @DisplayName("7일 기준으로 메트릭 이력을 조회한다")
    void shouldGetLast7DaysHistory() {
        // given
        service.record(buildMetrics("web-server", 30.0, 60.0, LocalDateTime.now().minusDays(8))); // 범위 밖
        service.record(buildMetrics("web-server", 50.0, 75.0, LocalDateTime.now().minusDays(5)));
        service.record(buildMetrics("web-server", 70.0, 80.0, LocalDateTime.now().minusDays(1)));

        // when
        List<MetricsSnapshot> history = service.getHistory("web-server", 7 * 24);

        // then
        assertThat(history).hasSize(2);
    }

    @Test
    @DisplayName("시간순 정렬로 조회된다")
    void shouldReturnHistoryInChronologicalOrder() {
        // given
        service.record(buildMetrics("web-server", 70.0, 80.0, LocalDateTime.now().minusHours(1)));
        service.record(buildMetrics("web-server", 50.0, 75.0, LocalDateTime.now().minusHours(5)));
        service.record(buildMetrics("web-server", 30.0, 60.0, LocalDateTime.now().minusHours(10)));

        // when
        List<MetricsSnapshot> history = service.getHistory("web-server", 24);

        // then
        assertThat(history).hasSize(3);
        assertThat(history.get(0).getCpuPercent()).isEqualTo(30.0); // 가장 오래된 것이 먼저
        assertThat(history.get(2).getCpuPercent()).isEqualTo(70.0);
    }

    @Test
    @DisplayName("컨테이너별로 분리하여 조회된다")
    void shouldReturnHistoryPerContainer() {
        // given
        service.record(buildMetrics("web-server", 50.0, 70.0, LocalDateTime.now().minusHours(1)));
        service.record(buildMetrics("db-server", 80.0, 90.0, LocalDateTime.now().minusHours(1)));

        // when
        List<MetricsSnapshot> webHistory = service.getHistory("web-server", 24);
        List<MetricsSnapshot> dbHistory = service.getHistory("db-server", 24);

        // then
        assertThat(webHistory).hasSize(1);
        assertThat(dbHistory).hasSize(1);
        assertThat(webHistory.get(0).getCpuPercent()).isEqualTo(50.0);
        assertThat(dbHistory.get(0).getCpuPercent()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("커스텀 날짜 범위로 메트릭 이력을 조회한다")
    void shouldGetHistoryByCustomRange() {
        // given
        LocalDateTime base = LocalDateTime.of(2026, 3, 10, 0, 0);
        service.record(buildMetrics("web-server", 10.0, 20.0, base.minusDays(1)));  // 범위 밖
        service.record(buildMetrics("web-server", 50.0, 60.0, base.plusHours(6)));  // 범위 안
        service.record(buildMetrics("web-server", 70.0, 80.0, base.plusHours(12))); // 범위 안
        service.record(buildMetrics("web-server", 90.0, 95.0, base.plusDays(2)));   // 범위 밖

        LocalDateTime from = base;
        LocalDateTime to = base.plusDays(1);

        // when
        List<MetricsSnapshot> result = service.getHistoryByRange("web-server", from, to);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCpuPercent()).isEqualTo(50.0);
        assertThat(result.get(1).getCpuPercent()).isEqualTo(70.0);
    }

    private ContainerMetrics buildMetrics(String name, double cpu, double memory, LocalDateTime time) {
        return ContainerMetrics.builder()
                .containerId(name + "-id")
                .containerName(name)
                .cpuPercent(cpu)
                .memoryPercent(memory)
                .memoryUsage(1024 * 1024 * 512)
                .memoryLimit(1024 * 1024 * 1024)
                .collectedAt(time)
                .build();
    }
}
