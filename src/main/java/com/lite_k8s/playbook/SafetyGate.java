package com.lite_k8s.playbook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.Map;

/**
 * Safety Gate
 *
 * Playbook 실행 전 위험도를 평가하고 실행 허용 여부를 결정
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SafetyGate {

    private final SafetyGateProperties properties;
    private final RiskAssessor riskAssessor;
    private final ServiceCriticalityResolver criticalityResolver;
    private final TimeBasedRiskElevator timeBasedRiskElevator;

    /**
     * Playbook 실행 허용 여부 평가
     *
     * @param playbook 실행할 Playbook
     * @param containerName 대상 컨테이너 이름
     * @param labels 컨테이너 라벨
     * @return 평가 결과
     */
    public SafetyGateResult evaluate(Playbook playbook, String containerName, Map<String, String> labels) {
        return evaluateAt(playbook, containerName, labels, LocalTime.now());
    }

    /**
     * 지정된 시간 기준으로 Playbook 실행 허용 여부 평가
     */
    public SafetyGateResult evaluateAt(Playbook playbook, String containerName, Map<String, String> labels, LocalTime time) {
        // Safety Gate가 비활성화된 경우 모두 허용
        if (!properties.isEnabled()) {
            log.debug("Safety Gate disabled, allowing playbook: {}", playbook.getName());
            return SafetyGateResult.bypassed();
        }

        // 서비스 중요도 결정
        ServiceCriticality criticality = criticalityResolver.resolve(containerName, labels);

        // 최종 위험도 산정
        RiskLevel finalRisk = riskAssessor.assessFinalRisk(playbook, criticality);

        // 시간대별 위험도 가중치 적용
        if (properties.isTimeBasedElevationEnabled()) {
            finalRisk = timeBasedRiskElevator.elevate(finalRisk, time);
        }

        log.info("Safety Gate evaluation - Playbook: {}, Container: {}, Criticality: {}, Risk: {}, Time: {}",
                playbook.getName(), containerName, criticality, finalRisk, time);

        // CRITICAL 위험도는 항상 차단 (수동 승인 필요)
        if (finalRisk == RiskLevel.CRITICAL) {
            return SafetyGateResult.blocked(
                    finalRisk,
                    criticality,
                    "CRITICAL risk level requires manual approval"
            );
        }

        // 고위험 조치 자동 차단 활성화 시 HIGH 위험도도 차단
        if (properties.isHighRiskAutoBlock() && finalRisk == RiskLevel.HIGH) {
            return SafetyGateResult.blocked(
                    finalRisk,
                    criticality,
                    "HIGH risk action blocked by auto-block policy"
            );
        }

        // HIGH 위험도 + CRITICAL 서비스도 차단
        if (finalRisk == RiskLevel.HIGH && criticality == ServiceCriticality.CRITICAL) {
            return SafetyGateResult.blocked(
                    finalRisk,
                    criticality,
                    "HIGH risk action on CRITICAL service requires manual approval"
            );
        }

        return SafetyGateResult.allowed(finalRisk, criticality);
    }
}
