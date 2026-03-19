package com.lite_k8s.approval;

import com.lite_k8s.playbook.RiskLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 승인 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final PendingApprovalRepository repository;

    /**
     * 승인 요청 생성
     */
    public PendingApproval requestApproval(String playbookName, String containerName, RiskLevel riskLevel) {
        PendingApproval approval = PendingApproval.create(playbookName, containerName, riskLevel);
        repository.save(approval);
        log.info("Approval requested - ID: {}, Playbook: {}, Container: {}, Risk: {}",
                approval.getId(), playbookName, containerName, riskLevel);
        return approval;
    }

    /**
     * 승인 처리
     */
    public ApprovalResult approve(String id, String approver) {
        return processApproval(id, approver, true);
    }

    /**
     * 거부 처리
     */
    public ApprovalResult reject(String id, String approver) {
        return processApproval(id, approver, false);
    }

    private ApprovalResult processApproval(String id, String approver, boolean approved) {
        Optional<PendingApproval> optional = repository.findById(id);

        if (optional.isEmpty()) {
            log.warn("Approval not found: {}", id);
            return ApprovalResult.failure("Approval request not found: " + id);
        }

        PendingApproval approval = optional.get();

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            log.warn("Approval already processed: {} - Status: {}", id, approval.getStatus());
            return ApprovalResult.failure("Approval request already processed: " + approval.getStatus());
        }

        if (approved) {
            approval.approve(approver);
            log.info("Approval approved - ID: {}, Approver: {}", id, approver);
        } else {
            approval.reject(approver);
            log.info("Approval rejected - ID: {}, Approver: {}", id, approver);
        }

        repository.save(approval);
        return ApprovalResult.success(approval);
    }

    /**
     * 대기 중인 요청 목록 조회
     */
    public List<PendingApproval> getPendingRequests() {
        return repository.findByStatus(ApprovalStatus.PENDING);
    }

    /**
     * ID로 조회
     */
    public Optional<PendingApproval> findById(String id) {
        return repository.findById(id);
    }

    /**
     * 만료된 요청 처리
     * @return 처리된 요청 수
     */
    public int processExpiredRequests() {
        List<PendingApproval> expired = repository.findExpiredPending();
        for (PendingApproval approval : expired) {
            approval.expire();
            repository.save(approval);
            log.info("Approval expired - ID: {}, Playbook: {}, Container: {}",
                    approval.getId(), approval.getPlaybookName(), approval.getContainerName());
        }
        return expired.size();
    }

    /**
     * 전체 요청 조회
     */
    public List<PendingApproval> findAll() {
        return repository.findAll();
    }
}
