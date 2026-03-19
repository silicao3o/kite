package com.lite_k8s.metrics;

import com.lite_k8s.model.ContainerMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MetricsDailyCompressorTest {

    private MetricsDailyCompressor compressor;
    private MetricsHistoryService historyService;
    private MetricsHistoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MetricsHistoryRepository();
        historyService = new MetricsHistoryService(repository);
        compressor = new MetricsDailyCompressor(repository, historyService);
    }

    @Test
    @DisplayName("하루치 메트릭을 일별 평균으로 압축한다")
    void shouldCompressDailyMetricsToAverage() {
        // given - 2일 전 데이터 4개
        LocalDateTime day = LocalDateTime.now().minusDays(2).withHour(0).withMinute(0).withSecond(0).withNano(0);
        record("web", 20.0, 40.0, day.plusHours(6));
        record("web", 40.0, 60.0, day.plusHours(12));
        record("web", 60.0, 80.0, day.plusHours(18));
        record("web", 80.0, 100.0, day.plusHours(23));

        // when
        compressor.compressDay("web", day.toLocalDate());

        // then: 4개 → 1개 (일별 평균)
        List<MetricsSnapshot> result = historyService.getHistoryByRange("web",
                day, day.plusDays(1));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCpuPercent()).isCloseTo(50.0, within(0.01));
        assertThat(result.get(0).getMemoryPercent()).isCloseTo(70.0, within(0.01));
    }

    @Test
    @DisplayName("압축된 데이터는 해당 날짜의 자정 시각을 가진다")
    void shouldSetMidnightTimestamp() {
        // given
        LocalDateTime day = LocalDateTime.now().minusDays(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
        record("web", 50.0, 70.0, day.plusHours(10));
        record("web", 70.0, 90.0, day.plusHours(20));

        // when
        compressor.compressDay("web", day.toLocalDate());

        // then
        List<MetricsSnapshot> result = historyService.getHistoryByRange("web", day, day.plusDays(1));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCollectedAt()).isEqualTo(day);
    }

    @Test
    @DisplayName("데이터가 없는 날은 압축하지 않는다")
    void shouldSkipDayWithNoData() {
        // given - 아무것도 기록 안 함
        LocalDate emptyDay = LocalDate.now().minusDays(5);

        // when / then - 예외 없이 정상 종료
        compressor.compressDay("web", emptyDay);
        List<MetricsSnapshot> result = historyService.getHistory("web", 30 * 24);
        assertThat(result).isEmpty();
    }

    private void record(String name, double cpu, double mem, LocalDateTime time) {
        historyService.record(ContainerMetrics.builder()
                .containerId(name + "-id").containerName(name)
                .cpuPercent(cpu).memoryPercent(mem)
                .collectedAt(time).build());
    }
}
