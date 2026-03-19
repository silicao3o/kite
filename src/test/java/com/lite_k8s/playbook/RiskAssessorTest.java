package com.lite_k8s.playbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RiskAssessorTest {

    private RiskAssessor assessor;

    @BeforeEach
    void setUp() {
        assessor = new RiskAssessor();
    }

    @Test
    @DisplayName("Playbook의 기본 위험도를 반환한다")
    void shouldReturnPlaybookBaseRiskLevel() {
        // given
        Playbook lowRiskPlaybook = Playbook.builder()
                .name("simple-restart")
                .riskLevel(RiskLevel.LOW)
                .build();

        Playbook highRiskPlaybook = Playbook.builder()
                .name("force-kill")
                .riskLevel(RiskLevel.HIGH)
                .build();

        // when & then
        assertThat(assessor.assessBaseRisk(lowRiskPlaybook)).isEqualTo(RiskLevel.LOW);
        assertThat(assessor.assessBaseRisk(highRiskPlaybook)).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    @DisplayName("액션 타입에 따른 위험도를 산정한다")
    void shouldAssessRiskByActionType() {
        // given
        Action delayAction = Action.builder().type("delay").build();
        Action emailAction = Action.builder().type("email").build();
        Action restartAction = Action.builder().type("container.restart").build();
        Action killAction = Action.builder().type("container.kill").build();

        // when & then
        assertThat(assessor.assessActionRisk(delayAction)).isEqualTo(RiskLevel.LOW);
        assertThat(assessor.assessActionRisk(emailAction)).isEqualTo(RiskLevel.LOW);
        assertThat(assessor.assessActionRisk(restartAction)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(assessor.assessActionRisk(killAction)).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    @DisplayName("Playbook의 모든 액션 중 가장 높은 위험도를 반환한다")
    void shouldReturnHighestActionRisk() {
        // given
        Playbook playbook = Playbook.builder()
                .name("mixed-actions")
                .actions(List.of(
                        Action.builder().type("delay").build(),
                        Action.builder().type("container.restart").build(),
                        Action.builder().type("email").build()
                ))
                .build();

        // when
        RiskLevel highestRisk = assessor.assessActionsRisk(playbook);

        // then
        assertThat(highestRisk).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    @DisplayName("서비스 중요도에 따라 위험도를 상향 조정한다")
    void shouldElevateRiskBasedOnServiceCriticality() {
        // given
        ServiceCriticality critical = ServiceCriticality.CRITICAL;
        ServiceCriticality normal = ServiceCriticality.NORMAL;

        // when & then
        // CRITICAL 서비스에 대한 LOW 조치 -> MEDIUM으로 상향
        assertThat(assessor.elevateRisk(RiskLevel.LOW, critical)).isEqualTo(RiskLevel.MEDIUM);
        // CRITICAL 서비스에 대한 MEDIUM 조치 -> HIGH로 상향
        assertThat(assessor.elevateRisk(RiskLevel.MEDIUM, critical)).isEqualTo(RiskLevel.HIGH);
        // NORMAL 서비스에 대한 LOW 조치 -> 그대로 LOW
        assertThat(assessor.elevateRisk(RiskLevel.LOW, normal)).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("최종 위험도를 종합적으로 산정한다")
    void shouldAssessFinalRisk() {
        // given
        Playbook playbook = Playbook.builder()
                .name("restart-playbook")
                .riskLevel(RiskLevel.LOW)
                .actions(List.of(
                        Action.builder().type("container.restart").build()
                ))
                .build();

        Map<String, String> context = Map.of(
                "containerId", "abc123",
                "containerName", "critical-service"
        );

        ServiceCriticality criticality = ServiceCriticality.HIGH;

        // when
        RiskLevel finalRisk = assessor.assessFinalRisk(playbook, criticality);

        // then
        // 액션 위험도 MEDIUM + HIGH 서비스 -> HIGH
        assertThat(finalRisk).isEqualTo(RiskLevel.HIGH);
    }
}
