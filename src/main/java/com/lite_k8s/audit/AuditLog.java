package com.lite_k8s.audit;

import com.lite_k8s.playbook.RiskLevel;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AI 조치 감사 로그
 *
 * 모든 AI 조치의 의도, 실행, 결과를 기록
 */
@Data
public class AuditLog {

    // 기본 정보
    private String id;
    private LocalDateTime timestamp;

    // 대상 컨테이너
    private String containerName;
    private String containerId;

    // Playbook 정보
    private String playbookName;
    private String actionType;

    // 조치 의도
    private String intent;

    // AI 판단 이유
    private String reasoning;

    // 위험도
    private RiskLevel riskLevel;

    // 승인 관련
    private boolean approvalRequired;
    private String approvedBy;
    private boolean approved;

    // 실행 결과
    private ExecutionResult executionResult;
    private String resultMessage;
    private LocalDateTime completedAt;

    @Builder
    public AuditLog(String containerName, String containerId, String playbookName,
                    String actionType, String intent, String reasoning,
                    RiskLevel riskLevel, boolean approvalRequired) {
        this.id = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.containerName = containerName;
        this.containerId = containerId;
        this.playbookName = playbookName;
        this.actionType = actionType;
        this.intent = intent;
        this.reasoning = reasoning;
        this.riskLevel = riskLevel;
        this.approvalRequired = approvalRequired;
        this.executionResult = ExecutionResult.PENDING;
    }

    /**
     * 성공 기록
     */
    public void recordSuccess(String message) {
        this.executionResult = ExecutionResult.SUCCESS;
        this.resultMessage = message;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 실패 기록
     */
    public void recordFailure(String message) {
        this.executionResult = ExecutionResult.FAILURE;
        this.resultMessage = message;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 차단 기록
     */
    public void recordBlocked(String reason) {
        this.executionResult = ExecutionResult.BLOCKED;
        this.resultMessage = reason;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 타임아웃 기록
     */
    public void recordTimeout() {
        this.executionResult = ExecutionResult.TIMEOUT;
        this.resultMessage = "승인 대기 타임아웃";
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 승인 정보 기록
     */
    public void recordApproval(String approver, boolean approved) {
        this.approvedBy = approver;
        this.approved = approved;
    }
}
