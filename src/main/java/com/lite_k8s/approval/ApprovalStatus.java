package com.lite_k8s.approval;

/**
 * 승인 상태
 */
public enum ApprovalStatus {
    PENDING,    // 승인 대기 중
    APPROVED,   // 승인됨
    REJECTED,   // 거부됨
    EXPIRED     // 만료됨 (타임아웃)
}
