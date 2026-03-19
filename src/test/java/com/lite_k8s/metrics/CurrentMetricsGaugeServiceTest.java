package com.lite_k8s.metrics;

import com.lite_k8s.model.ContainerMetrics;
import com.lite_k8s.service.MetricsScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CurrentMetricsGaugeServiceTest {

    private CurrentMetricsGaugeService service;
    private MetricsScheduler metricsScheduler;

    @BeforeEach
    void setUp() {
        metricsScheduler = mock(MetricsScheduler.class);
        service = new CurrentMetricsGaugeService(metricsScheduler);
    }

    @Test
    @DisplayName("컨테이너의 현재 CPU/메모리 게이지 데이터를 반환한다")
    void shouldReturnCurrentGaugeData() {
        // given
        ContainerMetrics metrics = ContainerMetrics.builder()
                .containerId("web-id").containerName("web-server")
                .cpuPercent(65.5).memoryPercent(78.2)
                .memoryUsage(800 * 1024 * 1024L)
                .memoryLimit(1024 * 1024 * 1024L)
                .build();
        when(metricsScheduler.getLatestMetrics("web-id")).thenReturn(Optional.of(metrics));

        // when
        Optional<GaugeData> gauge = service.getGauge("web-id");

        // then
        assertThat(gauge).isPresent();
        assertThat(gauge.get().getContainerName()).isEqualTo("web-server");
        assertThat(gauge.get().getCpuPercent()).isEqualTo(65.5);
        assertThat(gauge.get().getMemoryPercent()).isEqualTo(78.2);
    }

    @Test
    @DisplayName("메트릭이 없으면 empty를 반환한다")
    void shouldReturnEmptyWhenNoMetrics() {
        // given
        when(metricsScheduler.getLatestMetrics("unknown")).thenReturn(Optional.empty());

        // when
        Optional<GaugeData> gauge = service.getGauge("unknown");

        // then
        assertThat(gauge).isEmpty();
    }

    @Test
    @DisplayName("전체 컨테이너의 게이지 데이터를 한 번에 반환한다")
    void shouldReturnAllGauges() {
        // given
        Map<String, ContainerMetrics> allMetrics = Map.of(
                "web-id", ContainerMetrics.builder()
                        .containerId("web-id").containerName("web-server")
                        .cpuPercent(60.0).memoryPercent(70.0).build(),
                "db-id", ContainerMetrics.builder()
                        .containerId("db-id").containerName("db-server")
                        .cpuPercent(30.0).memoryPercent(85.0).build()
        );
        when(metricsScheduler.getAllCachedMetrics()).thenReturn(allMetrics);

        // when
        List<GaugeData> gauges = service.getAllGauges();

        // then
        assertThat(gauges).hasSize(2);
        assertThat(gauges.stream().map(GaugeData::getContainerName))
                .containsExactlyInAnyOrder("web-server", "db-server");
    }
}
