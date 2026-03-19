package com.lite_k8s.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GaugeControllerTest {

    private GaugeController controller;
    private CurrentMetricsGaugeService gaugeService;

    @BeforeEach
    void setUp() {
        gaugeService = mock(CurrentMetricsGaugeService.class);
        controller = new GaugeController(gaugeService);
    }

    @Test
    @DisplayName("전체 컨테이너 게이지 목록을 반환한다")
    void shouldReturnAllGauges() {
        // given
        List<GaugeData> gauges = List.of(
                GaugeData.builder().containerId("a").containerName("web").cpuPercent(40.0).memoryPercent(60.0).build(),
                GaugeData.builder().containerId("b").containerName("db").cpuPercent(20.0).memoryPercent(80.0).build()
        );
        when(gaugeService.getAllGauges()).thenReturn(gauges);

        // when
        List<GaugeData> result = controller.getAllGauges();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getContainerName()).isEqualTo("web");
    }

    @Test
    @DisplayName("특정 컨테이너 게이지를 반환한다")
    void shouldReturnGaugeForContainer() {
        // given
        GaugeData gauge = GaugeData.builder()
                .containerId("a").containerName("web")
                .cpuPercent(40.0).memoryPercent(60.0).build();
        when(gaugeService.getGauge("a")).thenReturn(Optional.of(gauge));

        // when
        Optional<GaugeData> result = controller.getGauge("a");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getCpuPercent()).isEqualTo(40.0);
    }

    @Test
    @DisplayName("존재하지 않는 컨테이너는 empty를 반환한다")
    void shouldReturnEmptyForUnknownContainer() {
        // given
        when(gaugeService.getGauge("unknown")).thenReturn(Optional.empty());

        // when
        Optional<GaugeData> result = controller.getGauge("unknown");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("자동 갱신 주기(초)를 반환한다")
    void shouldReturnRefreshInterval() {
        // when
        int interval = controller.getRefreshInterval();

        // then
        assertThat(interval).isEqualTo(30);
    }
}
