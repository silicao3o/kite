package com.lite_k8s.playbook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybookTest {

    @Test
    @DisplayName("Playbook은 이름과 트리거 조건을 가진다")
    void shouldHaveNameAndTrigger() {
        // given
        Playbook playbook = Playbook.builder()
                .name("container-restart")
                .description("컨테이너 재시작 플레이북")
                .trigger(Trigger.builder()
                        .event("container.die")
                        .conditions(Map.of("exitCode", "1"))
                        .build())
                .build();

        // then
        assertThat(playbook.getName()).isEqualTo("container-restart");
        assertThat(playbook.getDescription()).isEqualTo("컨테이너 재시작 플레이북");
        assertThat(playbook.getTrigger().getEvent()).isEqualTo("container.die");
    }

    @Test
    @DisplayName("Playbook은 여러 액션 스텝을 가진다")
    void shouldHaveMultipleActionSteps() {
        // given
        Playbook playbook = Playbook.builder()
                .name("oom-recovery")
                .trigger(Trigger.builder().event("container.oom").build())
                .actions(List.of(
                        Action.builder()
                                .name("wait")
                                .type("delay")
                                .params(Map.of("seconds", "10"))
                                .build(),
                        Action.builder()
                                .name("restart")
                                .type("container.restart")
                                .params(Map.of("containerId", "{{containerId}}"))
                                .build(),
                        Action.builder()
                                .name("notify")
                                .type("email")
                                .params(Map.of("subject", "OOM Recovery"))
                                .build()
                ))
                .build();

        // then
        assertThat(playbook.getActions()).hasSize(3);
        assertThat(playbook.getActions().get(0).getType()).isEqualTo("delay");
        assertThat(playbook.getActions().get(1).getType()).isEqualTo("container.restart");
        assertThat(playbook.getActions().get(2).getType()).isEqualTo("email");
    }

    @Test
    @DisplayName("Trigger는 여러 조건을 가질 수 있다")
    void triggerCanHaveMultipleConditions() {
        // given
        Trigger trigger = Trigger.builder()
                .event("container.die")
                .conditions(Map.of(
                        "exitCode", "137",
                        "oomKilled", "true"
                ))
                .build();

        // then
        assertThat(trigger.getConditions()).containsEntry("exitCode", "137");
        assertThat(trigger.getConditions()).containsEntry("oomKilled", "true");
    }

    @Test
    @DisplayName("Action은 조건부 실행을 지원한다")
    void actionSupportsConditionalExecution() {
        // given
        Action action = Action.builder()
                .name("conditional-restart")
                .type("container.restart")
                .when("{{restartCount}} < 3")
                .params(Map.of("containerId", "{{containerId}}"))
                .build();

        // then
        assertThat(action.getWhen()).isEqualTo("{{restartCount}} < 3");
    }

    @Test
    @DisplayName("Playbook은 위험도 레벨을 가진다")
    void playbookHasRiskLevel() {
        // given
        Playbook lowRisk = Playbook.builder()
                .name("simple-restart")
                .riskLevel(RiskLevel.LOW)
                .build();

        Playbook highRisk = Playbook.builder()
                .name("force-kill")
                .riskLevel(RiskLevel.HIGH)
                .build();

        // then
        assertThat(lowRisk.getRiskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(highRisk.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
    }
}
