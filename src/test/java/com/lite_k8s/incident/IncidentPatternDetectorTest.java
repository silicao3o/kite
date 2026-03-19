package com.lite_k8s.incident;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentPatternDetectorTest {

    private IncidentPatternDetector detector;
    private IncidentReportRepository repository;

    @BeforeEach
    void setUp() {
        repository = new IncidentReportRepository();
        detector = new IncidentPatternDetector(repository);
    }

    @Test
    @DisplayName("동일 컨테이너가 24시간 내 3회 이상 장애 시 패턴으로 감지된다")
    void shouldDetectPatternWhenContainerFailsThreeTimes() {
        // given - 같은 컨테이너가 3번 장애
        saveReport("web-server", "OOM 발생", LocalDateTime.now().minusHours(5));
        saveReport("web-server", "OOM 발생", LocalDateTime.now().minusHours(3));
        saveReport("web-server", "OOM 발생", LocalDateTime.now().minusHours(1));

        // when
        Optional<IncidentPattern> pattern = detector.detectPattern("web-server");

        // then
        assertThat(pattern).isPresent();
        assertThat(pattern.get().getContainerName()).isEqualTo("web-server");
        assertThat(pattern.get().getOccurrenceCount()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("24시간 내 2회 이하 장애는 패턴으로 감지되지 않는다")
    void shouldNotDetectPatternWhenContainerFailsTwice() {
        // given - 2번만 장애
        saveReport("web-server", "OOM 발생", LocalDateTime.now().minusHours(5));
        saveReport("web-server", "OOM 발생", LocalDateTime.now().minusHours(1));

        // when
        Optional<IncidentPattern> pattern = detector.detectPattern("web-server");

        // then
        assertThat(pattern).isEmpty();
    }

    @Test
    @DisplayName("24시간 초과 과거 장애는 패턴 집계에서 제외된다")
    void shouldExcludeOldIncidentsFromPattern() {
        // given - 2개는 최근, 1개는 25시간 전 (윈도우 밖)
        saveReport("web-server", "OOM 발생", LocalDateTime.now().minusHours(25));
        saveReport("web-server", "OOM 발생", LocalDateTime.now().minusHours(5));
        saveReport("web-server", "OOM 발생", LocalDateTime.now().minusHours(1));

        // when
        Optional<IncidentPattern> pattern = detector.detectPattern("web-server");

        // then
        assertThat(pattern).isEmpty(); // 윈도우 내 2개이므로 패턴 아님
    }

    @Test
    @DisplayName("반복 장애가 있는 모든 컨테이너 패턴을 한 번에 감지할 수 있다")
    void shouldDetectAllPatternsAcrossContainers() {
        // given
        saveReport("web-server", "OOM", LocalDateTime.now().minusHours(5));
        saveReport("web-server", "OOM", LocalDateTime.now().minusHours(3));
        saveReport("web-server", "OOM", LocalDateTime.now().minusHours(1));

        saveReport("db-server", "disk full", LocalDateTime.now().minusHours(4));
        saveReport("db-server", "disk full", LocalDateTime.now().minusHours(2));
        saveReport("db-server", "disk full", LocalDateTime.now().minusHours(1));

        saveReport("api-server", "crash", LocalDateTime.now().minusHours(1)); // 1번만

        // when
        List<IncidentPattern> patterns = detector.detectAllPatterns();

        // then
        assertThat(patterns).hasSize(2);
        assertThat(patterns.stream().map(IncidentPattern::getContainerName))
                .containsExactlyInAnyOrder("web-server", "db-server");
    }

    private void saveReport(String containerName, String summary, LocalDateTime createdAt) {
        IncidentReport report = IncidentReport.builder()
                .containerId(containerName + "-id")
                .containerName(containerName)
                .summary(summary)
                .build();
        report.setCreatedAt(createdAt);
        repository.save(report);
    }
}
