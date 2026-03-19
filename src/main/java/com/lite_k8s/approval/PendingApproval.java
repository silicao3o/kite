package com.lite_k8s.approval;

import com.lite_k8s.playbook.RiskLevel;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 승인 대기 중인 Playbook 실행 요청
 */
@Data
public class PendingApproval {

    private static final int EXPIRY_MINUTES = 5;

    private String id;
    private String playbookName;
    private String containerName;
    private RiskLevel riskLevel;
    private ApprovalStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime expiresAt;
    private String approvedBy;
    private LocalDateTime resolvedAt;

    /**
     * 새로운 승인 요청 생성
     */
    public static PendingApproval create(String playbookName, String containerName, RiskLevel riskLevel) {
        PendingApproval approval = new PendingApproval();
        approval.id = UUID.randomUUID().toString();
        approval.playbookName = playbookName;
        approval.containerName = containerName;
        approval.riskLevel = riskLevel;
        approval.status = ApprovalStatus.PENDING;
        approval.requestedAt = LocalDateTime.now();
        approval.expiresAt = approval.requestedAt.plusMinutes(EXPIRY_MINUTES);
        return approval;
    }

    /**
     * 승인 처리
     */
    public void approve(String approver) {
        this.status = ApprovalStatus.APPROVED;
        this.approvedBy = approver;
        this.resolvedAt = LocalDateTime.now();
    }

    /**
     * 거부 처리
     */
    public void reject(String approver) {
        this.status = ApprovalStatus.REJECTED;
        this.approvedBy = approver;
        this.resolvedAt = LocalDateTime.now();
    }

    /**
     * 만료 처리
     */
    public void expire() {
        this.status = ApprovalStatus.EXPIRED;
        this.resolvedAt = LocalDateTime.now();
    }

    /**
     * 만료 여부 확인
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
