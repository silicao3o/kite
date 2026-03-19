package com.lite_k8s.playbook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * 시간대별 위험도 가중치
 *
 * 업무 시간 내 조치는 위험도를 한 단계 상향
 */
@Slf4j
@Component
public class TimeBasedRiskElevator {

    private final LocalTime businessStartTime;
    private final LocalTime businessEndTime;

    public TimeBasedRiskElevator() {
        // 기본 업무 시간: 09:00 ~ 18:00
        this(LocalTime.of(9, 0), LocalTime.of(18, 0));
    }

    public TimeBasedRiskElevator(LocalTime businessStartTime, LocalTime businessEndTime) {
        this.businessStartTime = businessStartTime;
        this.businessEndTime = businessEndTime;
    }

    /**
     * 현재 시간 기준으로 위험도 상향
     */
    public RiskLevel elevate(RiskLevel baseRisk) {
        return elevate(baseRisk, LocalTime.now());
    }

    /**
     * 지정된 시간 기준으로 위험도 상향
     */
    public RiskLevel elevate(RiskLevel baseRisk, LocalTime time) {
        if (isBusinessHours(time)) {
            log.debug("Business hours detected at {}, elevating risk from {}", time, baseRisk);
            return elevateOneLevel(baseRisk);
        }
        return baseRisk;
    }

    /**
     * 업무 시간 여부 확인
     */
    public boolean isBusinessHours(LocalTime time) {
        // startTime <= time < endTime
        return !time.isBefore(businessStartTime) && time.isBefore(businessEndTime);
    }

    private RiskLevel elevateOneLevel(RiskLevel risk) {
        return switch (risk) {
            case LOW -> RiskLevel.MEDIUM;
            case MEDIUM -> RiskLevel.HIGH;
            case HIGH, CRITICAL -> RiskLevel.CRITICAL;
        };
    }
}
