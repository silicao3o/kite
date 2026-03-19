package com.lite_k8s.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeCodeClientTest {

    private ClaudeCodeClient client;

    @BeforeEach
    void setUp() {
        client = new ClaudeCodeClient();
    }

    @Test
    @DisplayName("프롬프트 생성")
    void shouldBuildPrompt() {
        // given
        AnomalyContext context = AnomalyContext.builder()
                .containerName("web-server")
                .containerId("abc123")
                .eventType("die")
                .exitCode(137)
                .oomKilled(true)
                .recentLogs("ERROR: Out of memory\nKilled process")
                .cpuPercent(95.5)
                .memoryPercent(99.8)
                .build();

        // when
        String prompt = client.buildPrompt(context);

        // then
        assertThat(prompt).contains("web-server");
        assertThat(prompt).contains("137");
        assertThat(prompt).contains("OOM");
        assertThat(prompt).contains("Out of memory");
    }

    @Test
    @DisplayName("CLI 명령어 생성")
    void shouldBuildCommand() {
        // given
        String prompt = "Analyze this container issue...";

        // when
        String[] command = client.buildCommand(prompt);

        // then
        assertThat(command).contains("claude");
        assertThat(command).contains("-p");
    }

    @Test
    @DisplayName("응답 파싱 - JSON 형식")
    void shouldParseJsonResponse() {
        // given
        String response = """
            {
                "action": "restart",
                "reasoning": "OOM으로 인한 종료. 메모리 limit 상향 필요.",
                "riskLevel": "MEDIUM",
                "confidence": 0.85
            }
            """;

        // when
        ClaudeResponse parsed = client.parseResponse(response);

        // then
        assertThat(parsed.getAction()).isEqualTo("restart");
        assertThat(parsed.getReasoning()).contains("OOM");
        assertThat(parsed.getRiskLevel()).isEqualTo("MEDIUM");
        assertThat(parsed.getConfidence()).isEqualTo(0.85);
    }

    @Test
    @DisplayName("응답 파싱 - 텍스트 형식 (JSON 없음)")
    void shouldParseTextResponse() {
        // given
        String response = """
            이 컨테이너는 OOM으로 인해 종료되었습니다.
            권장 조치: 재시작
            위험도: 중간
            """;

        // when
        ClaudeResponse parsed = client.parseResponse(response);

        // then
        assertThat(parsed.getRawResponse()).contains("OOM");
        assertThat(parsed.isJsonParsed()).isFalse();
    }

    @Test
    @DisplayName("타임아웃 설정")
    void shouldHaveTimeout() {
        // when
        int timeout = client.getTimeoutSeconds();

        // then
        assertThat(timeout).isGreaterThan(0);
        assertThat(timeout).isLessThanOrEqualTo(120);
    }
}
