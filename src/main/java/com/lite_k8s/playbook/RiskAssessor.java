package com.lite_k8s.playbook;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 위험도 산정기
 *
 * Playbook 실행의 최종 위험도를 산정
 */
@Component
public class RiskAssessor {

    /**
     * 액션 타입별 기본 위험도 매핑
     */
    private static final Map<String, RiskLevel> ACTION_RISK_MAP = Map.of(
            "delay", RiskLevel.LOW,
            "log", RiskLevel.LOW,
            "email", RiskLevel.LOW,
            "container.restart", RiskLevel.MEDIUM,
            "container.stop", RiskLevel.MEDIUM,
            "container.kill", RiskLevel.HIGH,
            "container.remove", RiskLevel.CRITICAL,
            "vm.restart", RiskLevel.HIGH,
            "vm.stop", RiskLevel.CRITICAL
    );

    /**
     * Playbook의 기본 위험도 반환
     */
    public RiskLevel assessBaseRisk(Playbook playbook) {
        return playbook.getRiskLevel() != null ? playbook.getRiskLevel() : RiskLevel.LOW;
    }

    /**
     * 액션 타입에 따른 위험도 산정
     */
    public RiskLevel assessActionRisk(Action action) {
        return ACTION_RISK_MAP.getOrDefault(action.getType(), RiskLevel.MEDIUM);
    }

    /**
     * Playbook의 모든 액션 중 가장 높은 위험도 반환
     */
    public RiskLevel assessActionsRisk(Playbook playbook) {
        if (playbook.getActions() == null || playbook.getActions().isEmpty()) {
            return RiskLevel.LOW;
        }

        return playbook.getActions().stream()
                .map(this::assessActionRisk)
                .max(this::compareRiskLevels)
                .orElse(RiskLevel.LOW);
    }

    /**
     * 서비스 중요도에 따라 위험도 상향 조정
     */
    public RiskLevel elevateRisk(RiskLevel baseRisk, ServiceCriticality criticality) {
        if (criticality == ServiceCriticality.LOW || criticality == ServiceCriticality.NORMAL) {
            return baseRisk;
        }

        // HIGH 또는 CRITICAL 서비스의 경우 위험도 한 단계 상향
        return switch (baseRisk) {
            case LOW -> RiskLevel.MEDIUM;
            case MEDIUM -> RiskLevel.HIGH;
            case HIGH -> RiskLevel.CRITICAL;
            case CRITICAL -> RiskLevel.CRITICAL;
        };
    }

    /**
     * 최종 위험도 종합 산정
     *
     * 1. 액션들의 최고 위험도 계산
     * 2. 서비스 중요도에 따라 상향 조정
     */
    public RiskLevel assessFinalRisk(Playbook playbook, ServiceCriticality serviceCriticality) {
        RiskLevel actionsRisk = assessActionsRisk(playbook);
        RiskLevel baseRisk = max(assessBaseRisk(playbook), actionsRisk);
        return elevateRisk(baseRisk, serviceCriticality);
    }

    private RiskLevel max(RiskLevel a, RiskLevel b) {
        return compareRiskLevels(a, b) >= 0 ? a : b;
    }

    private int compareRiskLevels(RiskLevel a, RiskLevel b) {
        return a.ordinal() - b.ordinal();
    }
}
