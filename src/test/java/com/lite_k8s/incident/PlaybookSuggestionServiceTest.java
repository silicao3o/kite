package com.lite_k8s.incident;

import com.lite_k8s.ai.ClaudeCodeClient;
import com.lite_k8s.ai.ClaudeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PlaybookSuggestionServiceTest {

    private SuggestionService suggestionService;
    private SuggestionRepository repository;
    private ClaudeCodeClient claudeCodeClient;

    @BeforeEach
    void setUp() {
        repository = new SuggestionRepository();
        claudeCodeClient = mock(ClaudeCodeClient.class);
        suggestionService = new SuggestionService(repository, claudeCodeClient);
    }

    @Test
    @DisplayName("Playbook 개선 제안을 생성하면 PLAYBOOK_IMPROVEMENT 타입으로 저장된다")
    void shouldGeneratePlaybookImprovementSuggestion() {
        // given
        when(claudeCodeClient.isEnabled()).thenReturn(true);
        when(claudeCodeClient.analyzeWithPrompt(anyString())).thenReturn(
                ClaudeResponse.success("notify",
                        "container-restart Playbook에 메모리 정리 스텝을 추가하고, " +
                        "재시작 지연을 10초에서 30초로 늘리세요.",
                        "LOW", 0.85, "raw")
        );

        IncidentPattern pattern = IncidentPattern.builder()
                .containerName("web-server")
                .occurrenceCount(5)
                .firstOccurrence(LocalDateTime.now().minusHours(10))
                .lastOccurrence(LocalDateTime.now().minusHours(1))
                .commonSummary("container-restart Playbook 실행 후에도 재발")
                .build();

        // when
        Suggestion suggestion = suggestionService.generatePlaybookSuggestion("container-restart", pattern);

        // then
        assertThat(suggestion.getType()).isEqualTo(Suggestion.Type.PLAYBOOK_IMPROVEMENT);
        assertThat(suggestion.getContent()).isNotBlank();
        assertThat(suggestion.getStatus()).isEqualTo(Suggestion.Status.PENDING);
    }

    @Test
    @DisplayName("AI 비활성화 시 기본 Playbook 개선 제안을 생성한다")
    void shouldGenerateDefaultPlaybookSuggestionWhenAiDisabled() {
        // given
        when(claudeCodeClient.isEnabled()).thenReturn(false);

        IncidentPattern pattern = IncidentPattern.builder()
                .containerName("web-server")
                .occurrenceCount(4)
                .firstOccurrence(LocalDateTime.now().minusHours(8))
                .lastOccurrence(LocalDateTime.now().minusHours(2))
                .commonSummary("OOM 발생")
                .build();

        // when
        Suggestion suggestion = suggestionService.generatePlaybookSuggestion("oom-recovery", pattern);

        // then
        assertThat(suggestion.getType()).isEqualTo(Suggestion.Type.PLAYBOOK_IMPROVEMENT);
        assertThat(suggestion.getContent()).contains("oom-recovery");
        assertThat(suggestion.getStatus()).isEqualTo(Suggestion.Status.PENDING);
    }
}
