package com.lite_k8s.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogAnalysisServiceTest {

    @Mock
    private AiClientSelector aiClientSelector;

    private LogAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new LogAnalysisService(aiClientSelector);
        when(aiClientSelector.isEnabled()).thenReturn(true);
    }

    @Test
    @DisplayName("로그 분석 요청 - 성공")
    void shouldAnalyzeLogs() {
        // given
        String containerId = "container-123";
        String containerName = "web-server";
        String logs = """
            2024-01-01T10:00:00Z ERROR Connection refused to database
            2024-01-01T10:00:01Z ERROR Retrying connection...
            2024-01-01T10:00:02Z FATAL Unable to connect, shutting down
            """;

        ClaudeResponse aiResponse = ClaudeResponse.builder()
                .action("notify")
                .reasoning("데이터베이스 연결 실패로 인한 종료. DB 서버 상태 확인 필요.")
                .riskLevel("MEDIUM")
                .confidence(0.9)
                .rawResponse("raw")
                .jsonParsed(true)
                .build();

        when(aiClientSelector.analyzeWithPrompt(any())).thenReturn(aiResponse);

        // when
        Optional<LogAnalysisResult> result = service.analyze(containerId, containerName, logs);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getRootCause()).contains("데이터베이스");
        assertThat(result.get().getSeverity()).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("AI 비활성화 시 빈 결과")
    void shouldReturnEmptyWhenDisabled() {
        // given
        when(aiClientSelector.isEnabled()).thenReturn(false);

        // when
        Optional<LogAnalysisResult> result = service.analyze("id", "name", "logs");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("빈 로그 시 빈 결과")
    void shouldReturnEmptyForEmptyLogs() {
        // when
        Optional<LogAnalysisResult> result = service.analyze("id", "name", "");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("AI 에러 응답 시 빈 결과")
    void shouldReturnEmptyOnAiError() {
        // given
        when(aiClientSelector.analyzeWithPrompt(any()))
                .thenReturn(ClaudeResponse.error("Timeout"));

        // when
        Optional<LogAnalysisResult> result = service.analyze("id", "name", "some logs");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("OOM 로그 분석")
    void shouldAnalyzeOomLogs() {
        // given
        String logs = """
            2024-01-01T10:00:00Z ERROR java.lang.OutOfMemoryError: Java heap space
            2024-01-01T10:00:01Z ERROR at com.example.Service.process(Service.java:42)
            """;

        ClaudeResponse aiResponse = ClaudeResponse.builder()
                .action("restart")
                .reasoning("Java 힙 메모리 부족. 메모리 limit 상향 또는 메모리 누수 점검 필요.")
                .riskLevel("HIGH")
                .confidence(0.95)
                .rawResponse("raw")
                .jsonParsed(true)
                .build();

        when(aiClientSelector.analyzeWithPrompt(any())).thenReturn(aiResponse);

        // when
        Optional<LogAnalysisResult> result = service.analyze("id", "name", logs);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getSeverity()).isEqualTo("HIGH");
        assertThat(result.get().getSuggestedActions()).isNotEmpty();
    }

    @Test
    @DisplayName("분석 결과에 권장 조치 포함")
    void shouldIncludeSuggestedActions() {
        // given
        ClaudeResponse aiResponse = ClaudeResponse.builder()
                .action("restart")
                .reasoning("프로세스 크래시. 재시작 권장.")
                .riskLevel("LOW")
                .confidence(0.85)
                .rawResponse("raw")
                .jsonParsed(true)
                .build();

        when(aiClientSelector.analyzeWithPrompt(any())).thenReturn(aiResponse);

        // when
        Optional<LogAnalysisResult> result = service.analyze("id", "name", "crash log");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getSuggestedActions()).contains("restart");
    }

    @Test
    @DisplayName("로그 컨텍스트에 컨테이너 정보 포함")
    void shouldIncludeContainerInfoInContext() {
        // given
        ClaudeResponse aiResponse = ClaudeResponse.builder()
                .action("notify")
                .reasoning("분석 완료")
                .riskLevel("LOW")
                .confidence(0.8)
                .rawResponse("raw")
                .jsonParsed(true)
                .build();

        when(aiClientSelector.analyzeWithPrompt(contains("web-server"))).thenReturn(aiResponse);

        // when
        Optional<LogAnalysisResult> result = service.analyze("abc123", "web-server", "some logs");

        // then
        assertThat(result).isPresent();
    }
}
