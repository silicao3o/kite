package com.lite_k8s.playbook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Safety Gate 평가 결과
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyGateResult {

    /**
     * 실행 허용 여부
     */
    private boolean allowed;

    /**
     * 최종 산정된 위험도
     */
    private RiskLevel finalRiskLevel;

    /**
     * 차단 또는 허용 사유
     */
    private String reason;

    /**
     * 서비스 중요도
     */
    private ServiceCriticality serviceCriticality;

    /**
     * 수동 승인 필요 여부
     */
    private boolean requiresApproval;

    public static SafetyGateResult allowed(RiskLevel riskLevel, ServiceCriticality criticality) {
        return SafetyGateResult.builder()
                .allowed(true)
                .finalRiskLevel(riskLevel)
                .serviceCriticality(criticality)
                .requiresApproval(false)
                .reason("Risk level acceptable")
                .build();
    }

    public static SafetyGateResult blocked(RiskLevel riskLevel, ServiceCriticality criticality, String reason) {
        return SafetyGateResult.builder()
                .allowed(false)
                .finalRiskLevel(riskLevel)
                .serviceCriticality(criticality)
                .requiresApproval(true)
                .reason(reason)
                .build();
    }

    public static SafetyGateResult bypassed() {
        return SafetyGateResult.builder()
                .allowed(true)
                .finalRiskLevel(null)
                .serviceCriticality(null)
                .requiresApproval(false)
                .reason("Safety Gate disabled")
                .build();
    }
}
