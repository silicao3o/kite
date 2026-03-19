package com.lite_k8s.incident;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentReportRepositoryTest {

    private IncidentReportRepository repository;

    @BeforeEach
    void setUp() {
        repository = new IncidentReportRepository();
    }

    @Test
    @DisplayName("리포트를 저장하고 ID로 조회할 수 있다")
    void shouldSaveAndFindById() {
        // given
        IncidentReport report = createReport("web-server", "OOM 발생");

        // when
        repository.save(report);

        // then
        Optional<IncidentReport> found = repository.findById(report.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getContainerName()).isEqualTo("web-server");
    }

    @Test
    @DisplayName("전체 조회 시 최신순으로 반환된다")
    void shouldFindAllOrderByCreatedAtDesc() {
        // given
        IncidentReport older = createReport("web-1", "첫 번째 장애");
        IncidentReport newer = createReport("web-2", "두 번째 장애");
        repository.save(older);
        repository.save(newer);

        // when
        List<IncidentReport> all = repository.findAll();

        // then
        assertThat(all).hasSize(2);
        assertThat(all.get(0).getContainerName()).isEqualTo("web-2");
    }

    @Test
    @DisplayName("컨테이너 이름으로 리포트를 조회할 수 있다")
    void shouldFindByContainerName() {
        // given
        repository.save(createReport("web-server", "OOM 발생"));
        repository.save(createReport("db-server", "디스크 부족"));
        repository.save(createReport("web-server", "CPU 과부하"));

        // when
        List<IncidentReport> webReports = repository.findByContainerName("web-server");

        // then
        assertThat(webReports).hasSize(2);
        assertThat(webReports).allMatch(r -> r.getContainerName().equals("web-server"));
    }

    private IncidentReport createReport(String containerName, String summary) {
        return IncidentReport.builder()
                .containerId(containerName + "-id")
                .containerName(containerName)
                .summary(summary)
                .build();
    }
}
