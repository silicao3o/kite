package com.lite_k8s.metrics;

import com.lite_k8s.model.HealingEvent;
import com.lite_k8s.repository.HealingEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HealingStatisticsServiceTest {

    private HealingStatisticsService service;
    private HealingEventRepository repository;

    @BeforeEach
    void setUp() {
        repository = new HealingEventRepository();
        service = new HealingStatisticsService(repository);
    }

    @Test
    @DisplayName("전체 자가치유 횟수를 계산한다")
    void shouldCountTotalHealingEvents() {
        // given
        repository.save(buildEvent("web", true));
        repository.save(buildEvent("web", true));
        repository.save(buildEvent("db", false));

        // when
        HealingStatistics stats = service.getStatistics();

        // then
        assertThat(stats.getTotalCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("성공률을 계산한다")
    void shouldCalculateSuccessRate() {
        // given
        repository.save(buildEvent("web", true));
        repository.save(buildEvent("web", true));
        repository.save(buildEvent("web", true));
        repository.save(buildEvent("web", false));

        // when
        HealingStatistics stats = service.getStatistics();

        // then
        assertThat(stats.getSuccessCount()).isEqualTo(3);
        assertThat(stats.getFailureCount()).isEqualTo(1);
        assertThat(stats.getSuccessRate()).isCloseTo(75.0, within(0.1));
    }

    @Test
    @DisplayName("이벤트가 없으면 성공률은 0이다")
    void shouldReturnZeroSuccessRateWhenNoEvents() {
        // when
        HealingStatistics stats = service.getStatistics();

        // then
        assertThat(stats.getTotalCount()).isEqualTo(0);
        assertThat(stats.getSuccessRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("컨테이너별 자가치유 횟수를 집계한다")
    void shouldCountHealingPerContainer() {
        // given
        repository.save(buildEvent("web-server", true));
        repository.save(buildEvent("web-server", true));
        repository.save(buildEvent("web-server", false));
        repository.save(buildEvent("db-server", true));

        // when
        HealingStatistics stats = service.getStatistics();

        // then
        assertThat(stats.getCountPerContainer()).containsEntry("web-server", 3L);
        assertThat(stats.getCountPerContainer()).containsEntry("db-server", 1L);
    }

    private HealingEvent buildEvent(String containerName, boolean success) {
        return HealingEvent.builder()
                .containerId(containerName + "-id")
                .containerName(containerName)
                .timestamp(LocalDateTime.now())
                .success(success)
                .restartCount(1)
                .message(success ? "성공" : "실패")
                .build();
    }
}
