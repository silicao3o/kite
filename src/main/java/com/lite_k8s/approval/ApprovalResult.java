package com.lite_k8s.approval;

import lombok.Getter;

/**
 * 승인/거부 처리 결과
 */
@Getter
public class ApprovalResult {

    private final boolean success;
    private final String message;
    private final PendingApproval approval;

    private ApprovalResult(boolean success, String message, PendingApproval approval) {
        this.success = success;
        this.message = message;
        this.approval = approval;
    }

    public static ApprovalResult success(PendingApproval approval) {
        return new ApprovalResult(true, "Success", approval);
    }

    public static ApprovalResult failure(String message) {
        return new ApprovalResult(false, message, null);
    }
}
