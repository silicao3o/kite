package com.lite_k8s.incident;

import com.lite_k8s.ai.ClaudeCodeClient;
import com.lite_k8s.ai.ClaudeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SuggestionServiceTest {

    private SuggestionService service;
    private SuggestionRepository suggestionRepository;
    private IncidentPatternDetector patternDetector;
    private ClaudeCodeClient claudeCodeClient;

    @BeforeEach
    void setUp() {
        IncidentReportRepository reportRepository = new IncidentReportRepository();
        suggestionRepository = new SuggestionRepository();
        patternDetector = new IncidentPatternDetector(reportRepository);
        claudeCodeClient = mock(ClaudeCodeClient.class);
        service = new SuggestionService(suggestionRepository, claudeCodeClient);

        // 반복 패턴 데이터 세팅
        for (int i = 0; i < 3; i++) {
            IncidentReport report = IncidentReport.builder()
                    .containerId("web-id")
                    .containerName("web-server")
                    .summary("OOM으로 종료")
                    .build();
            report.setCreatedAt(LocalDateTime.now().minusHours(5 - i));
            reportRepository.save(report);
        }
    }

    @Test
    @DisplayName("패턴 기반으로 설정 최적화 제안을 생성한다")
    void shouldGenerateSuggestionFromPattern() {
        // given
        IncidentPattern pattern = IncidentPattern.builder()
                .containerName("web-server")
                .occurrenceCount(3)
                .firstOccurrence(LocalDateTime.now().minusHours(5))
                .lastOccurrence(LocalDateTime.now().minusHours(1))
                .commonSummary("OOM으로 종료")
                .build();

        when(claudeCodeClient.isEnabled()).thenReturn(true);
        when(claudeCodeClient.analyzeWithPrompt(anyString())).thenReturn(
                ClaudeResponse.success("notify",
                        "메모리 한도를 현재의 2배로 증설하고, 힙 덤프 분석을 통해 메모리 누수 여부를 확인하세요.",
                        "HIGH", 0.9, "raw")
        );

        // when
        Suggestion suggestion = service.generateFromPattern(pattern);

        // then
        assertThat(suggestion.getId()).isNotNull();
        assertThat(suggestion.getContainerName()).isEqualTo("web-server");
        assertThat(suggestion.getContent()).isNotBlank();
        assertThat(suggestion.getType()).isEqualTo(Suggestion.Type.CONFIG_OPTIMIZATION);
        assertThat(suggestion.getStatus()).isEqualTo(Suggestion.Status.PENDING);
    }

    @Test
    @DisplayName("AI 비활성화 시 기본 제안을 생성한다")
    void shouldGenerateDefaultSuggestionWhenAiDisabled() {
        // given
        IncidentPattern pattern = IncidentPattern.builder()
                .containerName("web-server")
                .occurrenceCount(5)
                .firstOccurrence(LocalDateTime.now().minusHours(10))
                .lastOccurrence(LocalDateTime.now().minusHours(1))
                .commonSummary("OOM으로 종료")
                .build();

        when(claudeCodeClient.isEnabled()).thenReturn(false);

        // when
        Suggestion suggestion = service.generateFromPattern(pattern);

        // then
        assertThat(suggestion.getContent()).isNotBlank();
        assertThat(suggestion.getStatus()).isEqualTo(Suggestion.Status.PENDING);
    }

    @Test
    @DisplayName("제안을 승인할 수 있다")
    void shouldApproveSuggestion() {
        // given
        when(claudeCodeClient.isEnabled()).thenReturn(false);
        IncidentPattern pattern = IncidentPattern.builder()
                .containerName("web-server").occurrenceCount(3)
                .firstOccurrence(LocalDateTime.now().minusHours(3))
                .lastOccurrence(LocalDateTime.now()).commonSummary("OOM").build();

        Suggestion suggestion = service.generateFromPattern(pattern);

        // when
        service.approve(suggestion.getId());

        // then
        Suggestion updated = suggestionRepository.findById(suggestion.getId()).get();
        assertThat(updated.getStatus()).isEqualTo(Suggestion.Status.APPROVED);
    }

    @Test
    @DisplayName("제안을 거부할 수 있다")
    void shouldRejectSuggestion() {
        // given
        when(claudeCodeClient.isEnabled()).thenReturn(false);
        IncidentPattern pattern = IncidentPattern.builder()
                .containerName("web-server").occurrenceCount(3)
                .firstOccurrence(LocalDateTime.now().minusHours(3))
                .lastOccurrence(LocalDateTime.now()).commonSummary("OOM").build();

        Suggestion suggestion = service.generateFromPattern(pattern);

        // when
        service.reject(suggestion.getId());

        // then
        Suggestion updated = suggestionRepository.findById(suggestion.getId()).get();
        assertThat(updated.getStatus()).isEqualTo(Suggestion.Status.REJECTED);
    }

    @Test
    @DisplayName("PENDING 상태 제안 목록을 조회할 수 있다")
    void shouldFindPendingSuggestions() {
        // given
        when(claudeCodeClient.isEnabled()).thenReturn(false);
        IncidentPattern p1 = IncidentPattern.builder().containerName("web").occurrenceCount(3)
                .firstOccurrence(LocalDateTime.now().minusHours(3))
                .lastOccurrence(LocalDateTime.now()).commonSummary("OOM").build();
        IncidentPattern p2 = IncidentPattern.builder().containerName("db").occurrenceCount(4)
                .firstOccurrence(LocalDateTime.now().minusHours(4))
                .lastOccurrence(LocalDateTime.now()).commonSummary("disk").build();

        Suggestion s1 = service.generateFromPattern(p1);
        service.generateFromPattern(p2);
        service.approve(s1.getId());

        // when
        List<Suggestion> pending = service.findPending();

        // then
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getContainerName()).isEqualTo("db");
    }
}
