package com.lite_k8s.playbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SafetyGateTest {

    @Mock
    private SafetyGateProperties properties;

    private SafetyGate safetyGate;

    @BeforeEach
    void setUp() {
        lenient().when(properties.isEnabled()).thenReturn(true);
        lenient().when(properties.isTimeBasedElevationEnabled()).thenReturn(false);
        lenient().when(properties.isHighRiskAutoBlock()).thenReturn(false);
        lenient().when(properties.toServiceCriticalityRules()).thenReturn(List.of(
                new ServiceCriticalityRule("db-*", ServiceCriticality.CRITICAL),
                new ServiceCriticalityRule("api-*", ServiceCriticality.HIGH)
        ));

        RiskAssessor riskAssessor = new RiskAssessor();
        ServiceCriticalityResolver criticalityResolver = new ServiceCriticalityResolver(
                properties.toServiceCriticalityRules()
        );
        TimeBasedRiskElevator timeBasedElevator = new TimeBasedRiskElevator();

        safetyGate = new SafetyGate(properties, riskAssessor, criticalityResolver, timeBasedElevator);
    }

    @Test
    @DisplayName("LOW 위험도 Playbook은 실행 허용")
    void shouldAllowLowRiskPlaybook() {
        // given
        Playbook playbook = Playbook.builder()
                .name("notify")
                .riskLevel(RiskLevel.LOW)
                .actions(List.of(
                        Action.builder().type("email").build()
                ))
                .build();

        String containerName = "worker-email";
        Map<String, String> labels = Map.of();

        // when
        SafetyGateResult result = safetyGate.evaluate(playbook, containerName, labels);

        // then
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getFinalRiskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("CRITICAL 위험도 Playbook은 실행 차단")
    void shouldBlockCriticalRiskPlaybook() {
        // given
        Playbook playbook = Playbook.builder()
                .name("force-remove")
                .riskLevel(RiskLevel.CRITICAL)
                .actions(List.of(
                        Action.builder().type("container.remove").build()
                ))
                .build();

        String containerName = "db-postgres";
        Map<String, String> labels = Map.of();

        // when
        SafetyGateResult result = safetyGate.evaluate(playbook, containerName, labels);

        // then
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getFinalRiskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(result.getReason()).contains("CRITICAL");
    }

    @Test
    @DisplayName("Safety Gate가 비활성화되면 모든 Playbook 허용")
    void shouldAllowAllWhenDisabled() {
        // given
        when(properties.isEnabled()).thenReturn(false);

        Playbook criticalPlaybook = Playbook.builder()
                .name("dangerous")
                .riskLevel(RiskLevel.CRITICAL)
                .build();

        // when
        SafetyGateResult result = safetyGate.evaluate(criticalPlaybook, "test", Map.of());

        // then
        assertThat(result.isAllowed()).isTrue();
    }

    @Test
    @DisplayName("업무 시간 내 조치는 위험도가 상향된다")
    void shouldElevateRiskDuringBusinessHours() {
        // given
        when(properties.isTimeBasedElevationEnabled()).thenReturn(true);

        Playbook playbook = Playbook.builder()
                .name("restart")
                .riskLevel(RiskLevel.LOW)
                .actions(List.of(
                        Action.builder().type("container.restart").build()
                ))
                .build();

        String containerName = "worker-task";
        LocalTime businessHour = LocalTime.of(14, 0);  // 오후 2시

        // when
        SafetyGateResult result = safetyGate.evaluateAt(playbook, containerName, Map.of(), businessHour);

        // then
        // 기본 MEDIUM + 업무시간 상향 = HIGH
        assertThat(result.getFinalRiskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    @DisplayName("고위험 조치 자동 차단이 활성화되면 HIGH 위험도도 차단한다")
    void shouldBlockHighRiskWhenAutoBlockEnabled() {
        // given
        when(properties.isHighRiskAutoBlock()).thenReturn(true);

        Playbook playbook = Playbook.builder()
                .name("kill-container")
                .riskLevel(RiskLevel.HIGH)
                .actions(List.of(
                        Action.builder().type("container.kill").build()
                ))
                .build();

        String containerName = "worker-task";

        // when
        SafetyGateResult result = safetyGate.evaluate(playbook, containerName, Map.of());

        // then
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getFinalRiskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.getReason()).contains("HIGH");
    }

    @Test
    @DisplayName("고위험 조치 자동 차단이 비활성화되면 HIGH 위험도는 허용한다")
    void shouldAllowHighRiskWhenAutoBlockDisabled() {
        // given
        when(properties.isHighRiskAutoBlock()).thenReturn(false);

        Playbook playbook = Playbook.builder()
                .name("kill-container")
                .riskLevel(RiskLevel.HIGH)
                .actions(List.of(
                        Action.builder().type("container.kill").build()
                ))
                .build();

        String containerName = "worker-task";

        // when
        SafetyGateResult result = safetyGate.evaluate(playbook, containerName, Map.of());

        // then
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getFinalRiskLevel()).isEqualTo(RiskLevel.HIGH);
    }
}
