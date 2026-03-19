package com.lite_k8s.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MetricsAggregationServiceTest {

    private MetricsAggregationService service;
    private MetricsHistoryService historyService;

    @BeforeEach
    void setUp() {
        historyService = new MetricsHistoryService(new MetricsHistoryRepository());
        service = new MetricsAggregationService(historyService);
    }

    @Test
    @DisplayName("시간대별 평균 CPU/메모리를 집계한다")
    void shouldAggregateHourlyAverage() {
        // given - 10시에 2개, 11시에 2개
        LocalDateTime base = LocalDateTime.of(2026, 3, 17, 10, 0);
        record("web", 40.0, 60.0, base.plusMinutes(10));
        record("web", 60.0, 80.0, base.plusMinutes(50));
        record("web", 70.0, 90.0, base.plusHours(1).plusMinutes(10));
        record("web", 90.0, 70.0, base.plusHours(1).plusMinutes(50));

        // when
        List<HourlyAggregate> result = service.getHourlyAverage("web", base, base.plusHours(2));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAvgCpu()).isCloseTo(50.0, within(0.01));
        assertThat(result.get(0).getAvgMemory()).isCloseTo(70.0, within(0.01));
        assertThat(result.get(1).getAvgCpu()).isCloseTo(80.0, within(0.01));
    }

    @Test
    @DisplayName("시간대별 최대 CPU/메모리를 집계한다")
    void shouldAggregateHourlyMax() {
        // given
        LocalDateTime base = LocalDateTime.of(2026, 3, 17, 10, 0);
        record("web", 40.0, 60.0, base.plusMinutes(10));
        record("web", 75.0, 85.0, base.plusMinutes(50));
        record("web", 55.0, 95.0, base.plusHours(1).plusMinutes(20));

        // when
        List<HourlyAggregate> result = service.getHourlyMax("web", base, base.plusHours(2));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMaxCpu()).isEqualTo(75.0);
        assertThat(result.get(0).getMaxMemory()).isEqualTo(85.0);
        assertThat(result.get(1).getMaxMemory()).isEqualTo(95.0);
    }

    @Test
    @DisplayName("데이터가 없는 시간대는 결과에 포함되지 않는다")
    void shouldExcludeEmptyHours() {
        // given - 10시에만 데이터
        LocalDateTime base = LocalDateTime.of(2026, 3, 17, 10, 0);
        record("web", 50.0, 70.0, base.plusMinutes(30));

        // when - 10시~12시 조회
        List<HourlyAggregate> result = service.getHourlyAverage("web", base, base.plusHours(3));

        // then - 데이터 있는 1시간만 반환
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHour()).isEqualTo(base.withMinute(0).withSecond(0).withNano(0));
    }

    @Test
    @DisplayName("컨테이너별 전체 집계 통계를 반환한다")
    void shouldReturnContainerStats() {
        // given
        LocalDateTime now = LocalDateTime.now();
        record("web", 40.0, 60.0, now.minusHours(2));
        record("web", 80.0, 90.0, now.minusHours(1));

        // when
        ContainerStats stats = service.getContainerStats("web", 24);

        // then
        assertThat(stats.getContainerName()).isEqualTo("web");
        assertThat(stats.getAvgCpu()).isCloseTo(60.0, within(0.01));
        assertThat(stats.getMaxCpu()).isEqualTo(80.0);
        assertThat(stats.getMinCpu()).isEqualTo(40.0);
        assertThat(stats.getAvgMemory()).isCloseTo(75.0, within(0.01));
    }

    private void record(String name, double cpu, double mem, LocalDateTime time) {
        historyService.record(com.lite_k8s.model.ContainerMetrics.builder()
                .containerId(name + "-id").containerName(name)
                .cpuPercent(cpu).memoryPercent(mem)
                .collectedAt(time).build());
    }
}
