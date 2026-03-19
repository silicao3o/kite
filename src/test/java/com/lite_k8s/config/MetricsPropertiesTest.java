package com.lite_k8s.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsPropertiesTest {

    @Test
    @DisplayName("메트릭 수집 주기 기본값은 15초이다")
    void shouldHaveDefaultCollectionIntervalOf15Seconds() {
        // given
        MonitorProperties properties = new MonitorProperties();

        // when
        MonitorProperties.Metrics metrics = properties.getMetrics();

        // then
        assertThat(metrics.getCollectionIntervalSeconds()).isEqualTo(15);
    }

    @Test
    @DisplayName("메트릭 수집 주기를 설정할 수 있다")
    void shouldAllowCustomCollectionInterval() {
        // given
        MonitorProperties properties = new MonitorProperties();
        MonitorProperties.Metrics metrics = new MonitorProperties.Metrics();
        metrics.setCollectionIntervalSeconds(30);

        // when
        properties.setMetrics(metrics);

        // then
        assertThat(properties.getMetrics().getCollectionIntervalSeconds()).isEqualTo(30);
    }

    @Test
    @DisplayName("메트릭 수집 활성화 기본값은 true이다")
    void shouldHaveDefaultEnabledTrue() {
        // given
        MonitorProperties properties = new MonitorProperties();

        // when
        MonitorProperties.Metrics metrics = properties.getMetrics();

        // then
        assertThat(metrics.isEnabled()).isTrue();
    }
}
