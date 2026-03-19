package com.lite_k8s.playbook;

/**
 * 서비스 중요도 레벨
 *
 * 서비스의 비즈니스 중요도를 나타냄
 */
public enum ServiceCriticality {
    LOW,        // 낮은 중요도 - 개발/테스트 서비스
    NORMAL,     // 일반 중요도 - 일반 서비스
    HIGH,       // 높은 중요도 - 중요 서비스
    CRITICAL    // 치명적 중요도 - 핵심 비즈니스 서비스
}
