package com.lite_k8s.incident;

import com.lite_k8s.ai.AiClientSelector;
import com.lite_k8s.ai.ClaudeResponse;
import com.lite_k8s.model.ContainerDeathEvent;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IncidentReportServiceTest {

    @Mock
    private IncidentReportJpaRepository mockJpa;

    @Mock
    private AiClientSelector aiClientSelector;

    private IncidentReportService service;
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

        when(mockJpa.findById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return store.stream().filter(r -> r.getId().equals(id)).findFirst();
        });

        when(mockJpa.findByContainerNameOrderByCreatedAtDesc(anyString())).thenAnswer(inv -> {
            String containerName = inv.getArgument(0);
            return store.stream()
                    .filter(r -> r.getContainerName().equals(containerName))
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .toList();
        });

        repository = new IncidentReportRepository(mockJpa);
        service = new IncidentReportService(repository, aiClientSelector);
    }

    @Test
    @DisplayName("컨테이너 종료 이벤트로 장애 리포트를 생성한다")
    void shouldCreateIncidentReportFromDeathEvent() {
        // given
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerId("abc123")
                .containerName("web-server")
                .exitCode(137L)
                .oomKilled(true)
                .deathTime(LocalDateTime.now())
                .lastLogs("java.lang.OutOfMemoryError: Java heap space")
                .build();

        when(aiClientSelector.isEnabled()).thenReturn(false);

        // when
        IncidentReport report = service.createReport(event);

        // then
        assertThat(report.getId()).isNotNull();
        assertThat(report.getContainerName()).isEqualTo("web-server");
        assertThat(report.getContainerId()).isEqualTo("abc123");
        assertThat(report.getSummary()).isNotBlank();
        assertThat(report.getStatus()).isEqualTo(IncidentReport.Status.OPEN);

        // 저장 확인
        assertThat(repository.findById(report.getId())).isPresent();
    }

    @Test
    @DisplayName("AI 분석이 활성화된 경우 근본 원인이 채워진다")
    void shouldFillRootCauseWhenAiEnabled() {
        // given
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerId("abc123")
                .containerName("web-server")
                .exitCode(137L)
                .oomKilled(true)
                .deathTime(LocalDateTime.now())
                .lastLogs("OutOfMemoryError")
                .build();

        when(aiClientSelector.isEnabled()).thenReturn(true);
        when(aiClientSelector.analyzeWithPrompt(anyString())).thenReturn(
                ClaudeResponse.success("notify", "메모리 한도 초과로 OOM Killer에 의해 종료됨",
                        "HIGH", 0.9, "raw")
        );

        // when
        IncidentReport report = service.createReport(event);

        // then
        assertThat(report.getRootCause()).contains("OOM");
        assertThat(report.getStatus()).isEqualTo(IncidentReport.Status.ANALYZING);
    }

    @Test
    @DisplayName("AI 분석 완료 후 리포트를 CLOSED 상태로 갱신한다")
    void shouldCloseReportAfterAnalysis() {
        // given
        IncidentReport report = IncidentReport.builder()
                .containerId("abc123")
                .containerName("web-server")
                .summary("OOM 발생")
                .build();
        repository.save(report);

        // when
        service.closeReport(report.getId(), "메모리 부족", List.of("메모리 한도 증설", "힙 덤프 분석"));

        // then
        IncidentReport updated = repository.findById(report.getId()).get();
        assertThat(updated.getStatus()).isEqualTo(IncidentReport.Status.CLOSED);
        assertThat(updated.getRootCause()).isEqualTo("메모리 부족");
        assertThat(updated.getSuggestions()).containsExactly("메모리 한도 증설", "힙 덤프 분석");
        assertThat(updated.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("컨테이너별 리포트 목록을 조회할 수 있다")
    void shouldFindReportsByContainerName() {
        // given
        ContainerDeathEvent event1 = ContainerDeathEvent.builder()
                .containerId("id1").containerName("web-server")
                .exitCode(1L).deathTime(LocalDateTime.now()).build();
        ContainerDeathEvent event2 = ContainerDeathEvent.builder()
                .containerId("id2").containerName("db-server")
                .exitCode(1L).deathTime(LocalDateTime.now()).build();

        when(aiClientSelector.isEnabled()).thenReturn(false);

        service.createReport(event1);
        service.createReport(event2);

        // when
        List<IncidentReport> webReports = service.findByContainerName("web-server");

        // then
        assertThat(webReports).hasSize(1);
        assertThat(webReports.get(0).getContainerName()).isEqualTo("web-server");
    }
}
