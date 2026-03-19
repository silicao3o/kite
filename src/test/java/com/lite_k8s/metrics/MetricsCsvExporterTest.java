package com.lite_k8s.metrics;

import com.lite_k8s.model.ContainerMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsCsvExporterTest {

    private MetricsCsvExporter exporter;
    private MetricsHistoryService historyService;

    @BeforeEach
    void setUp() {
        historyService = new MetricsHistoryService(new MetricsHistoryRepository());
        exporter = new MetricsCsvExporter(historyService);

        historyService.record(ContainerMetrics.builder()
                .containerId("web-id").containerName("web-server")
                .cpuPercent(45.5).memoryPercent(70.2)
                .memoryUsage(512 * 1024 * 1024L).memoryLimit(1024 * 1024 * 1024L)
                .collectedAt(LocalDateTime.now().minusHours(2))
                .build());
        historyService.record(ContainerMetrics.builder()
                .containerId("web-id").containerName("web-server")
                .cpuPercent(55.0).memoryPercent(75.0)
                .memoryUsage(768 * 1024 * 1024L).memoryLimit(1024 * 1024 * 1024L)
                .collectedAt(LocalDateTime.now().minusHours(1))
                .build());
    }

    @Test
    @DisplayName("메트릭 히스토리를 CSV 문자열로 내보낸다")
    void shouldExportMetricsToCsv() {
        // when
        String csv = exporter.exportMetrics("web-server", 24);

        // then
        assertThat(csv).contains("timestamp,container,cpu_percent,memory_percent");
        assertThat(csv).contains("web-server");
        assertThat(csv).contains("45.5");
        assertThat(csv).contains("55.0");
    }

    @Test
    @DisplayName("CSV 헤더가 첫 번째 줄에 포함된다")
    void shouldIncludeHeaderAsFirstLine() {
        // when
        String csv = exporter.exportMetrics("web-server", 24);

        // then
        String[] lines = csv.split("\n");
        assertThat(lines[0]).startsWith("timestamp,container");
    }

    @Test
    @DisplayName("데이터가 없으면 헤더만 반환된다")
    void shouldReturnOnlyHeaderWhenNoData() {
        // when
        String csv = exporter.exportMetrics("non-existent", 24);

        // then
        String[] lines = csv.strip().split("\n");
        assertThat(lines).hasSize(1);
        assertThat(lines[0]).startsWith("timestamp,container");
    }
}
