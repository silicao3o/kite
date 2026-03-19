package com.lite_k8s.ai;

import com.lite_k8s.playbook.Action;
import com.lite_k8s.playbook.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiResponseConverterTest {

    private AiResponseConverter converter;

    @BeforeEach
    void setUp() {
        converter = new AiResponseConverter();
    }

    @Test
    @DisplayName("restart 액션 변환")
    void shouldConvertRestartAction() {
        // given
        ClaudeResponse response = ClaudeResponse.success(
                "restart",
                "OOM으로 인한 종료. 재시작 필요.",
                "MEDIUM",
                0.85,
                "raw"
        );

        // when
        List<Action> actions = converter.convert(response, "container-123");

        // then
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).getType()).isEqualTo("container.restart");
        assertThat(actions.get(0).getParams()).containsEntry("containerId", "container-123");
    }

    @Test
    @DisplayName("kill 액션 변환")
    void shouldConvertKillAction() {
        // given
        ClaudeResponse response = ClaudeResponse.success(
                "kill",
                "무한 루프 감지. 강제 종료 필요.",
                "HIGH",
                0.90,
                "raw"
        );

        // when
        List<Action> actions = converter.convert(response, "container-456");

        // then
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).getType()).isEqualTo("container.kill");
    }

    @Test
    @DisplayName("notify 액션 변환")
    void shouldConvertNotifyAction() {
        // given
        ClaudeResponse response = ClaudeResponse.success(
                "notify",
                "수동 확인 필요.",
                "LOW",
                0.70,
                "raw"
        );

        // when
        List<Action> actions = converter.convert(response, "container-789");

        // then
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).getType()).isEqualTo("notify");
    }

    @Test
    @DisplayName("ignore 액션은 빈 목록 반환")
    void shouldReturnEmptyForIgnoreAction() {
        // given
        ClaudeResponse response = ClaudeResponse.success(
                "ignore",
                "정상적인 종료. 조치 불필요.",
                "LOW",
                0.95,
                "raw"
        );

        // when
        List<Action> actions = converter.convert(response, "container-abc");

        // then
        assertThat(actions).isEmpty();
    }

    @Test
    @DisplayName("위험도 레벨 변환")
    void shouldConvertRiskLevel() {
        // given/when/then
        assertThat(converter.toRiskLevel("LOW")).isEqualTo(RiskLevel.LOW);
        assertThat(converter.toRiskLevel("MEDIUM")).isEqualTo(RiskLevel.MEDIUM);
        assertThat(converter.toRiskLevel("HIGH")).isEqualTo(RiskLevel.HIGH);
        assertThat(converter.toRiskLevel("CRITICAL")).isEqualTo(RiskLevel.CRITICAL);
        assertThat(converter.toRiskLevel("unknown")).isEqualTo(RiskLevel.MEDIUM);
        assertThat(converter.toRiskLevel(null)).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    @DisplayName("에러 응답은 빈 목록 반환")
    void shouldReturnEmptyForErrorResponse() {
        // given
        ClaudeResponse response = ClaudeResponse.error("Timeout");

        // when
        List<Action> actions = converter.convert(response, "container-xyz");

        // then
        assertThat(actions).isEmpty();
    }

    @Test
    @DisplayName("JSON 파싱 실패 응답은 빈 목록 반환")
    void shouldReturnEmptyForTextResponse() {
        // given
        ClaudeResponse response = ClaudeResponse.text("Some text without JSON");

        // when
        List<Action> actions = converter.convert(response, "container-xyz");

        // then
        assertThat(actions).isEmpty();
    }
}
