package com.lite_k8s.metrics;

import com.lite_k8s.model.ContainerMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MultiContainerMetricsServiceTest {

    private MultiContainerMetricsService service;
    private MetricsHistoryService historyService;

    @BeforeEach
    void setUp() {
        historyService = new MetricsHistoryService(new MetricsHistoryRepository());
        service = new MultiContainerMetricsService(historyService);

        // web-server 메트릭 3개
        record("web-server", 40.0, 60.0, LocalDateTime.now().minusHours(2));
        record("web-server", 55.0, 65.0, LocalDateTime.now().minusHours(1));
        record("web-server", 70.0, 70.0, LocalDateTime.now());

        // db-server 메트릭 2개
        record("db-server", 20.0, 80.0, LocalDateTime.now().minusHours(1));
        record("db-server", 25.0, 85.0, LocalDateTime.now());
    }

    @Test
    @DisplayName("여러 컨테이너의 메트릭을 컨테이너명 키로 묶어 반환한다")
    void shouldReturnMetricsGroupedByContainer() {
        // when
        Map<String, List<MetricsSnapshot>> result =
                service.getComparisonData(List.of("web-server", "db-server"), 24);

        // then
        assertThat(result).containsKeys("web-server", "db-server");
        assertThat(result.get("web-server")).hasSize(3);
        assertThat(result.get("db-server")).hasSize(2);
    }

    @Test
    @DisplayName("존재하지 않는 컨테이너는 빈 리스트로 반환된다")
    void shouldReturnEmptyListForUnknownContainer() {
        // when
        Map<String, List<MetricsSnapshot>> result =
                service.getComparisonData(List.of("web-server", "unknown"), 24);

        // then
        assertThat(result.get("unknown")).isEmpty();
    }

    @Test
    @DisplayName("단일 컨테이너도 동일한 구조로 반환된다")
    void shouldHandleSingleContainer() {
        // when
        Map<String, List<MetricsSnapshot>> result =
                service.getComparisonData(List.of("web-server"), 24);

        // then
        assertThat(result).containsOnlyKeys("web-server");
        assertThat(result.get("web-server")).hasSize(3);
    }

    @Test
    @DisplayName("각 컨테이너 메트릭은 시간순 정렬된다")
    void shouldReturnMetricsInChronologicalOrder() {
        // when
        Map<String, List<MetricsSnapshot>> result =
                service.getComparisonData(List.of("web-server"), 24);

        // then
        List<MetricsSnapshot> snapshots = result.get("web-server");
        assertThat(snapshots.get(0).getCpuPercent()).isEqualTo(40.0);
        assertThat(snapshots.get(2).getCpuPercent()).isEqualTo(70.0);
    }

    private void record(String name, double cpu, double mem, LocalDateTime time) {
        historyService.record(ContainerMetrics.builder()
                .containerId(name + "-id").containerName(name)
                .cpuPercent(cpu).memoryPercent(mem)
                .collectedAt(time).build());
    }
}
