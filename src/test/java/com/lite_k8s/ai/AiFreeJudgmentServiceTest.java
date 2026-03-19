package com.lite_k8s.ai;

import com.lite_k8s.model.ContainerDeathEvent;
import com.lite_k8s.playbook.Action;
import com.lite_k8s.playbook.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiFreeJudgmentServiceTest {

    @Mock
    private ClaudeCodeClient claudeCodeClient;

    @Mock
    private AnomalyContextBuilder contextBuilder;

    @Mock
    private AiResponseConverter responseConverter;

    private AiFreeJudgmentService service;

    @BeforeEach
    void setUp() {
        service = new AiFreeJudgmentService(claudeCodeClient, contextBuilder, responseConverter, 0.6);
    }

    @Test
    @DisplayName("AI가 활성화되지 않으면 빈 결과 반환")
    void shouldReturnEmptyWhenAiDisabled() {
        // given
        when(claudeCodeClient.isEnabled()).thenReturn(false);
        ContainerDeathEvent event = createEvent("web-server");

        // when
        Optional<AiJudgmentResult> result = service.judge(event);

        // then
        assertThat(result).isEmpty();
        verify(claudeCodeClient, never()).analyze(any());
    }

    @Test
    @DisplayName("AI 분석 성공 시 판단 결과 반환")
    void shouldReturnJudgmentWhenAiAnalyzes() {
        // given
        when(claudeCodeClient.isEnabled()).thenReturn(true);

        AnomalyContext context = AnomalyContext.builder()
                .containerName("web-server")
                .containerId("abc123")
                .eventType("die")
                .build();
        when(contextBuilder.fromEvent(any())).thenReturn(contextBuilder);
        when(contextBuilder.build()).thenReturn(context);

        ClaudeResponse aiResponse = ClaudeResponse.success(
                "restart",
                "OOM 감지. 재시작 권장.",
                "MEDIUM",
                0.85,
                "raw"
        );
        when(claudeCodeClient.analyze(context)).thenReturn(aiResponse);

        List<Action> actions = List.of(
                Action.builder().type("container.restart").build()
        );
        when(responseConverter.convert(aiResponse, "abc123")).thenReturn(actions);
        when(responseConverter.toRiskLevel("MEDIUM")).thenReturn(RiskLevel.MEDIUM);

        ContainerDeathEvent event = createEvent("web-server");

        // when
        Optional<AiJudgmentResult> result = service.judge(event);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getActions()).hasSize(1);
        assertThat(result.get().getReasoning()).contains("OOM");
        assertThat(result.get().getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.get().getConfidence()).isEqualTo(0.85);
    }

    @Test
    @DisplayName("AI 에러 응답 시 빈 결과 반환")
    void shouldReturnEmptyWhenAiReturnsError() {
        // given
        when(claudeCodeClient.isEnabled()).thenReturn(true);

        AnomalyContext context = AnomalyContext.builder()
                .containerName("web-server")
                .containerId("abc123")
                .build();
        when(contextBuilder.fromEvent(any())).thenReturn(contextBuilder);
        when(contextBuilder.build()).thenReturn(context);

        ClaudeResponse errorResponse = ClaudeResponse.error("Timeout");
        when(claudeCodeClient.analyze(context)).thenReturn(errorResponse);

        ContainerDeathEvent event = createEvent("web-server");

        // when
        Optional<AiJudgmentResult> result = service.judge(event);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("AI ignore 응답 시 빈 결과 반환")
    void shouldReturnEmptyWhenAiReturnsIgnore() {
        // given
        when(claudeCodeClient.isEnabled()).thenReturn(true);

        AnomalyContext context = AnomalyContext.builder()
                .containerName("web-server")
                .containerId("abc123")
                .build();
        when(contextBuilder.fromEvent(any())).thenReturn(contextBuilder);
        when(contextBuilder.build()).thenReturn(context);

        ClaudeResponse ignoreResponse = ClaudeResponse.success(
                "ignore",
                "정상 종료",
                "LOW",
                0.95,
                "raw"
        );
        when(claudeCodeClient.analyze(context)).thenReturn(ignoreResponse);
        when(responseConverter.convert(ignoreResponse, "abc123")).thenReturn(List.of());

        ContainerDeathEvent event = createEvent("web-server");

        // when
        Optional<AiJudgmentResult> result = service.judge(event);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("신뢰도가 임계값 미만이면 빈 결과 반환")
    void shouldReturnEmptyWhenConfidenceTooLow() {
        // given
        service = new AiFreeJudgmentService(claudeCodeClient, contextBuilder, responseConverter, 0.7);
        when(claudeCodeClient.isEnabled()).thenReturn(true);

        AnomalyContext context = AnomalyContext.builder()
                .containerName("web-server")
                .containerId("abc123")
                .build();
        when(contextBuilder.fromEvent(any())).thenReturn(contextBuilder);
        when(contextBuilder.build()).thenReturn(context);

        ClaudeResponse lowConfidenceResponse = ClaudeResponse.success(
                "restart",
                "불확실한 판단",
                "MEDIUM",
                0.5,  // 임계값 0.7 미만
                "raw"
        );
        when(claudeCodeClient.analyze(context)).thenReturn(lowConfidenceResponse);

        ContainerDeathEvent event = createEvent("web-server");

        // when
        Optional<AiJudgmentResult> result = service.judge(event);

        // then
        assertThat(result).isEmpty();
    }

    private ContainerDeathEvent createEvent(String name) {
        return ContainerDeathEvent.builder()
                .containerName(name)
                .containerId("abc123")
                .action("die")
                .exitCode(137L)
                .build();
    }
}
