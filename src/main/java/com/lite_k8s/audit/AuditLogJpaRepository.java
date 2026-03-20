package com.lite_k8s.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogJpaRepository extends JpaRepository<AuditLog, String> {
    List<AuditLog> findAllByOrderByTimestampDesc();
    List<AuditLog> findByContainerIdOrderByTimestampDesc(String containerId);
    List<AuditLog> findByPlaybookNameOrderByTimestampDesc(String playbookName);
    List<AuditLog> findByExecutionResultOrderByTimestampDesc(ExecutionResult result);
    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime from, LocalDateTime to);
    List<AuditLog> findByTimestampBeforeOrderByTimestampDesc(LocalDateTime cutoff, Pageable pageable);
    List<AuditLog> findByTimestampBefore(LocalDateTime cutoff);
    List<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
