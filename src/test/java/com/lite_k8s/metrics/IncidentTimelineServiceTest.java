package com.lite_k8s.metrics;

import com.lite_k8s.incident.IncidentReport;
import com.lite_k8s.incident.IncidentReportJpaRepository;
import com.lite_k8s.incident.IncidentReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IncidentTimelineServiceTest {

    @Mock
    private IncidentReportJpaRepository mockJpa;

    private IncidentTimelineService service;
    private IncidentReportRepository repository;

    // In-memory store to simulate JPA behavior
    private final List<IncidentReport> store = new ArrayList<>();

    @BeforeEach
    void setUp() {
        store.clear();

        when(mockJpa.save(any(IncidentReport.class))).thenAnswer(inv -> {
            IncidentReport report = inv.getArgument(0);
            store.removeIf(r -> r.getId().equals(report.getId()));
            store.add(report);
            return report;
        });

        when(mockJpa.findAllByOrderByCreatedAtDesc()).thenAnswer(inv ->
                store.stream()
                        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                        .toList()
        );

        repository = new IncidentReportRepository(mockJpa);
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
