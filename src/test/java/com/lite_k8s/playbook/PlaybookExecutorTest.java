package com.lite_k8s.playbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaybookExecutorTest {

    @Mock
    private ActionHandler containerRestartHandler;

    @Mock
    private ActionHandler delayHandler;

    private PlaybookExecutor executor;

    @BeforeEach
    void setUp() {
        Map<String, ActionHandler> handlers = Map.of(
                "container.restart", containerRestartHandler,
                "delay", delayHandler
        );
        executor = new PlaybookExecutor(handlers);
    }

    @Test
    @DisplayName("Playbook의 모든 액션을 순서대로 실행한다")
    void shouldExecuteAllActionsInOrder() {
        // given
        Playbook playbook = Playbook.builder()
                .name("container-restart")
                .actions(List.of(
                        Action.builder()
                                .name("wait")
                                .type("delay")
                                .params(Map.of("seconds", "5"))
                                .build(),
                        Action.builder()
                                .name("restart")
                                .type("container.restart")
                                .params(Map.of("containerId", "abc123"))
                                .build()
                ))
                .build();

        Map<String, String> context = Map.of("containerId", "abc123");

        when(delayHandler.execute(any(), any())).thenReturn(ActionResult.success());
        when(containerRestartHandler.execute(any(), any())).thenReturn(ActionResult.success());

        // when
        PlaybookResult result = executor.execute(playbook, context);

        // then
        assertThat(result.isSuccess()).isTrue();
        verify(delayHandler).execute(any(), any());
        verify(containerRestartHandler).execute(any(), any());
    }

    @Test
    @DisplayName("액션 실행 실패 시 중단하고 실패 결과를 반환한다")
    void shouldStopOnActionFailure() {
        // given
        Playbook playbook = Playbook.builder()
                .name("failing-playbook")
                .actions(List.of(
                        Action.builder()
                                .name("fail-step")
                                .type("container.restart")
                                .params(Map.of("containerId", "abc123"))
                                .build(),
                        Action.builder()
                                .name("never-reached")
                                .type("delay")
                                .params(Map.of("seconds", "5"))
                                .build()
                ))
                .build();

        Map<String, String> context = Map.of("containerId", "abc123");

        when(containerRestartHandler.execute(any(), any()))
                .thenReturn(ActionResult.failure("Container not found"));

        // when
        PlaybookResult result = executor.execute(playbook, context);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("Container not found");
        verify(containerRestartHandler).execute(any(), any());
        verify(delayHandler, never()).execute(any(), any());
    }

    @Test
    @DisplayName("템플릿 변수를 컨텍스트 값으로 치환한다")
    void shouldSubstituteTemplateVariables() {
        // given
        Playbook playbook = Playbook.builder()
                .name("template-test")
                .actions(List.of(
                        Action.builder()
                                .name("restart")
                                .type("container.restart")
                                .params(Map.of("containerId", "{{containerId}}"))
                                .build()
                ))
                .build();

        Map<String, String> context = Map.of("containerId", "real-container-id");

        when(containerRestartHandler.execute(any(), any())).thenReturn(ActionResult.success());

        // when
        executor.execute(playbook, context);

        // then
        verify(containerRestartHandler).execute(
                argThat(action -> action.getParams().get("containerId").equals("real-container-id")),
                eq(context)
        );
    }
}
