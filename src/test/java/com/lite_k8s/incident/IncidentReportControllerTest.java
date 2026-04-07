package com.lite_k8s.incident;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class IncidentReportControllerTest {

    private IncidentReportController controller;
    private IncidentReportService service;

    @BeforeEach
    void setUp() {
        service = mock(IncidentReportService.class);
        controller = new IncidentReportController(service);
    }

    @Test
    @DisplayName("/incidents 페이지는 전체 리포트 목록을 모델에 담아 반환한다")
    void shouldReturnIncidentsPage() {
        // given
        Model model = new ExtendedModelMap();
        IncidentReport report = IncidentReport.builder()
                .containerId("id1")
                .containerName("web-server")
                .summary("OOM 발생")
                .build();
        Page<IncidentReport> page = new PageImpl<>(List.of(report));
        when(service.findAll(anyInt(), anyInt())).thenReturn(page);
        when(service.countByStatus(any())).thenReturn(0L);

        // when
        String view = controller.incidentsPage(0, 50, model);

        // then
        assertThat(view).isEqualTo("incidents");
        assertThat(model.asMap()).containsKey("reports");
        @SuppressWarnings("unchecked")
        List<IncidentReport> reports = (List<IncidentReport>) model.asMap().get("reports");
        assertThat(reports).hasSize(1);
    }

    @Test
    @DisplayName("/incidents/{id} 페이지는 특정 리포트 상세를 모델에 담아 반환한다")
    void shouldReturnIncidentDetailPage() {
        // given
        Model model = new ExtendedModelMap();
        IncidentReport report = IncidentReport.builder()
                .containerId("id1")
                .containerName("web-server")
                .summary("OOM 발생")
                .build();
        when(service.findById(report.getId())).thenReturn(java.util.Optional.of(report));

        // when
        String view = controller.incidentDetailPage(report.getId(), model);

        // then
        assertThat(view).isEqualTo("incident-detail");
        assertThat(model.asMap().get("report")).isEqualTo(report);
    }
}
