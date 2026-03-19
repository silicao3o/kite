package com.lite_k8s.playbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimeBasedRiskElevatorTest {

    private TimeBasedRiskElevator elevator;

    @BeforeEach
    void setUp() {
        // 업무 시간: 09:00 ~ 18:00
        elevator = new TimeBasedRiskElevator(
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)
        );
    }

    @Test
    @DisplayName("업무 시간 내 조치는 위험도를 상향한다")
    void shouldElevateRiskDuringBusinessHours() {
        // given
        LocalTime businessHour = LocalTime.of(14, 0);  // 오후 2시

        // when & then
        assertThat(elevator.elevate(RiskLevel.LOW, businessHour)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(elevator.elevate(RiskLevel.MEDIUM, businessHour)).isEqualTo(RiskLevel.HIGH);
        assertThat(elevator.elevate(RiskLevel.HIGH, businessHour)).isEqualTo(RiskLevel.CRITICAL);
        assertThat(elevator.elevate(RiskLevel.CRITICAL, businessHour)).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    @DisplayName("업무 시간 외 조치는 위험도를 유지한다")
    void shouldMaintainRiskOutsideBusinessHours() {
        // given
        LocalTime afterHours = LocalTime.of(22, 0);  // 밤 10시

        // when & then
        assertThat(elevator.elevate(RiskLevel.LOW, afterHours)).isEqualTo(RiskLevel.LOW);
        assertThat(elevator.elevate(RiskLevel.MEDIUM, afterHours)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(elevator.elevate(RiskLevel.HIGH, afterHours)).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    @DisplayName("업무 시간 경계에서 올바르게 판단한다")
    void shouldHandleBoundaryCorrectly() {
        // given
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(18, 0);
        LocalTime beforeStart = LocalTime.of(8, 59);
        LocalTime afterEnd = LocalTime.of(18, 1);

        // when & then
        assertThat(elevator.isBusinessHours(startTime)).isTrue();
        assertThat(elevator.isBusinessHours(endTime)).isFalse();  // 18:00는 종료 시간이므로 업무 외
        assertThat(elevator.isBusinessHours(beforeStart)).isFalse();
        assertThat(elevator.isBusinessHours(afterEnd)).isFalse();
    }

    @Test
    @DisplayName("현재 시간 기준으로 위험도를 상향한다")
    void shouldElevateBasedOnCurrentTime() {
        // given
        RiskLevel baseRisk = RiskLevel.LOW;

        // when
        RiskLevel elevated = elevator.elevate(baseRisk);

        // then - 현재 시간에 따라 결과가 달라질 수 있음
        // 업무 시간이면 MEDIUM, 아니면 LOW
        LocalTime now = LocalTime.now();
        if (elevator.isBusinessHours(now)) {
            assertThat(elevated).isEqualTo(RiskLevel.MEDIUM);
        } else {
            assertThat(elevated).isEqualTo(RiskLevel.LOW);
        }
    }
}
