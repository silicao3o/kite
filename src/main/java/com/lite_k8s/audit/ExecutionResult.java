package com.lite_k8s.audit;

/**
 * 조치 실행 결과
 */
public enum ExecutionResult {
    PENDING,    // 실행 시작 (결과 미확정)
    SUCCESS,    // 성공
    FAILURE,    // 실패
    BLOCKED     // 차단됨 (Safety Gate)
}
