package com.lite_k8s.playbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DelayHandlerTest {

    private DelayHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DelayHandler();
    }

    @Test
    @DisplayName("지정된 시간만큼 대기 후 성공 결과를 반환한다")
    void shouldWaitAndReturnSuccess() {
        // given
        Action action = Action.builder()
                .name("wait")
                .type("delay")
                .params(Map.of("seconds", "1"))
                .build();
        Map<String, String> context = Map.of();

        long startTime = System.currentTimeMillis();

        // when
        ActionResult result = handler.execute(action, context);

        long elapsed = System.currentTimeMillis() - startTime;

        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(elapsed).isGreaterThanOrEqualTo(1000);
    }

    @Test
    @DisplayName("seconds 파라미터가 없으면 실패 결과를 반환한다")
    void shouldReturnFailureWhenSecondsMissing() {
        // given
        Action action = Action.builder()
                .name("wait")
                .type("delay")
                .params(Map.of())
                .build();
        Map<String, String> context = Map.of();

        // when
        ActionResult result = handler.execute(action, context);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("seconds");
    }

    @Test
    @DisplayName("잘못된 seconds 값이면 실패 결과를 반환한다")
    void shouldReturnFailureWhenSecondsInvalid() {
        // given
        Action action = Action.builder()
                .name("wait")
                .type("delay")
                .params(Map.of("seconds", "invalid"))
                .build();
        Map<String, String> context = Map.of();

        // when
        ActionResult result = handler.execute(action, context);

        // then
        assertThat(result.isSuccess()).isFalse();
    }
}
