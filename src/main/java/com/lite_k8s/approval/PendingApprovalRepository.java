package com.lite_k8s.approval;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 승인 대기 저장소
 */
@Repository
@RequiredArgsConstructor
public class PendingApprovalRepository {

    private final PendingApprovalJpaRepository jpa;

    public void save(PendingApproval approval) {
        jpa.save(approval);
    }

    public Optional<PendingApproval> findById(String id) {
        return jpa.findById(id);
    }

    public List<PendingApproval> findByStatus(ApprovalStatus status) {
        return jpa.findByStatus(status);
    }

    public List<PendingApproval> findAll() {
        return jpa.findAll();
    }

    public List<PendingApproval> findExpiredPending() {
        return jpa.findByStatusAndExpiresAtBefore(ApprovalStatus.PENDING, LocalDateTime.now());
    }

    public void delete(String id) {
        jpa.deleteById(id);
    }

    public void clear() {
        jpa.deleteAll();
    }
}
