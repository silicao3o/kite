package com.lite_k8s.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MetricsAggregationControllerTest {

    private MetricsAggregationController controller;
    private MetricsAggregationService aggregationService;

    @BeforeEach
    void setUp() {
        aggregationService = mock(MetricsAggregationService.class);
        controller = new MetricsAggregationController(aggregationService);
    }

    @Test
    @DisplayName("시간대별 평균 API가 서비스를 호출하고 결과를 반환한다")
    void shouldReturnHourlyAverage() {
        // given
        LocalDateTime from = LocalDateTime.of(2026, 3, 17, 10, 0);
        LocalDateTime to = LocalDateTime.of(2026, 3, 17, 12, 0);
        List<HourlyAggregate> expected = List.of(
                HourlyAggregate.builder().hour(from).avgCpu(50.0).avgMemory(70.0).build()
        );
        when(aggregationService.getHourlyAverage("web", from, to)).thenReturn(expected);

        // when
        List<HourlyAggregate> result = controller.getHourlyAverage("web", from.toString(), to.toString());

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAvgCpu()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("시간대별 최대 API가 서비스를 호출하고 결과를 반환한다")
    void shouldReturnHourlyMax() {
        // given
        LocalDateTime from = LocalDateTime.of(2026, 3, 17, 10, 0);
        LocalDateTime to = LocalDateTime.of(2026, 3, 17, 12, 0);
        List<HourlyAggregate> expected = List.of(
                HourlyAggregate.builder().hour(from).maxCpu(80.0).maxMemory(90.0).build()
        );
        when(aggregationService.getHourlyMax("web", from, to)).thenReturn(expected);

        // when
        List<HourlyAggregate> result = controller.getHourlyMax("web", from.toString(), to.toString());

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMaxCpu()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("컨테이너 집계 통계 API가 서비스를 호출하고 결과를 반환한다")
    void shouldReturnContainerStats() {
        // given
        ContainerStats stats = ContainerStats.builder()
                .containerName("web").avgCpu(60.0).maxCpu(80.0).minCpu(40.0)
                .avgMemory(75.0).maxMemory(90.0).minMemory(60.0).sampleCount(2)
                .build();
        when(aggregationService.getContainerStats("web", 24)).thenReturn(stats);

        // when
        ContainerStats result = controller.getContainerStats("web", 24);

        // then
        assertThat(result.getAvgCpu()).isEqualTo(60.0);
        assertThat(result.getMaxCpu()).isEqualTo(80.0);
        assertThat(result.getSampleCount()).isEqualTo(2);
    }
}
