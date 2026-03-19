package com.lite_k8s.audit;

/**
 * 조치 실행 결과
 */
public enum ExecutionResult {
    PENDING,    // 실행 대기 중
    SUCCESS,    // 성공
    FAILURE,    // 실패
    BLOCKED,    // 차단됨 (Safety Gate)
    TIMEOUT     // 승인 타임아웃
}
