package com.lite_k8s.playbook;

/**
 * Playbook 위험도 레벨
 */
public enum RiskLevel {
    LOW,        // 낮은 위험 - 자동 실행 가능
    MEDIUM,     // 중간 위험 - 주의 필요
    HIGH,       // 높은 위험 - 승인 권장
    CRITICAL    // 치명적 - 항상 수동 승인 필요
}
