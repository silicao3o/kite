package com.lite_k8s.metrics;

import com.lite_k8s.incident.IncidentReport;
import com.lite_k8s.incident.IncidentReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentTimelineServiceTest {

    private IncidentTimelineService service;
    private IncidentReportRepository repository;

    @BeforeEach
    void setUp() {
        repository = new IncidentReportRepository();
        service = new IncidentTimelineService(repository);
    }

    @Test
    @DisplayName("장애 타임라인은 최신순으로 정렬된다")
    void shouldReturnTimelineInDescendingOrder() {
        // given
        saveReport("web-server", LocalDateTime.now().minusDays(3));
        saveReport("db-server", LocalDateTime.now().minusDays(1));
        saveReport("api-server", LocalDateTime.now().minusHours(2));

        // when
        List<TimelineEntry> timeline = service.getTimeline(7);

        // then
        assertThat(timeline).hasSize(3);
        assertThat(timeline.get(0).getContainerName()).isEqualTo("api-server");
        assertThat(timeline.get(2).getContainerName()).isEqualTo("web-server");
    }

    @Test
    @DisplayName("지정한 일수 범위 내 장애만 포함된다")
    void shouldFilterByDays() {
        // given
        saveReport("old-container", LocalDateTime.now().minusDays(8));
        saveReport("recent-container", LocalDateTime.now().minusDays(3));

        // when
        List<TimelineEntry> timeline = service.getTimeline(7);

        // then
        assertThat(timeline).hasSize(1);
        assertThat(timeline.get(0).getContainerName()).isEqualTo("recent-container");
    }

    @Test
    @DisplayName("타임라인 항목에는 컨테이너명, 발생 시각, 요약이 포함된다")
    void shouldIncludeRequiredFields() {
        // given
        saveReport("web-server", LocalDateTime.now().minusHours(1));

        // when
        List<TimelineEntry> timeline = service.getTimeline(7);

        // then
        TimelineEntry entry = timeline.get(0);
        assertThat(entry.getContainerName()).isEqualTo("web-server");
        assertThat(entry.getOccurredAt()).isNotNull();
        assertThat(entry.getSummary()).isNotBlank();
        assertThat(entry.getStatus()).isNotNull();
    }

    private void saveReport(String containerName, LocalDateTime createdAt) {
        IncidentReport report = IncidentReport.builder()
                .containerId(containerName + "-id")
                .containerName(containerName)
                .summary(containerName + " 장애 발생")
                .build();
        report.setCreatedAt(createdAt);
        repository.save(report);
    }
}
