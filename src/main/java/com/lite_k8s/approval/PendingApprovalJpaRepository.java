package com.lite_k8s.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PendingApprovalJpaRepository extends JpaRepository<PendingApproval, String> {
    List<PendingApproval> findByStatus(ApprovalStatus status);
    List<PendingApproval> findByStatusAndExpiresAtBefore(ApprovalStatus status, LocalDateTime now);
}
