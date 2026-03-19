package com.lite_k8s.playbook;

import com.lite_k8s.service.DockerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContainerRestartHandlerTest {

    @Mock
    private DockerService dockerService;

    private ContainerRestartHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ContainerRestartHandler(dockerService);
    }

    @Test
    @DisplayName("컨테이너 재시작 성공 시 성공 결과를 반환한다")
    void shouldReturnSuccessWhenRestartSucceeds() {
        // given
        Action action = Action.builder()
                .name("restart")
                .type("container.restart")
                .params(Map.of("containerId", "abc123"))
                .build();
        Map<String, String> context = Map.of("containerId", "abc123");

        when(dockerService.restartContainer("abc123")).thenReturn(true);

        // when
        ActionResult result = handler.execute(action, context);

        // then
        assertThat(result.isSuccess()).isTrue();
        verify(dockerService).restartContainer("abc123");
    }

    @Test
    @DisplayName("컨테이너 재시작 실패 시 실패 결과를 반환한다")
    void shouldReturnFailureWhenRestartFails() {
        // given
        Action action = Action.builder()
                .name("restart")
                .type("container.restart")
                .params(Map.of("containerId", "abc123"))
                .build();
        Map<String, String> context = Map.of();

        when(dockerService.restartContainer("abc123")).thenReturn(false);

        // when
        ActionResult result = handler.execute(action, context);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("abc123");
    }

    @Test
    @DisplayName("containerId 파라미터가 없으면 실패 결과를 반환한다")
    void shouldReturnFailureWhenContainerIdMissing() {
        // given
        Action action = Action.builder()
                .name("restart")
                .type("container.restart")
                .params(Map.of())
                .build();
        Map<String, String> context = Map.of();

        // when
        ActionResult result = handler.execute(action, context);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("containerId");
    }
}
