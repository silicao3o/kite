package com.lite_k8s.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AI 조치 감사 로그 저장소
 *
 * Append-Only: 저장만 가능, 수정/삭제 불가 (보존 정책 스케줄러 제외)
 */
@Repository
@RequiredArgsConstructor
public class AuditLogRepository {

    private final AuditLogJpaRepository jpa;

    public void save(AuditLog log) {
        jpa.save(log);
    }

    public Optional<AuditLog> findById(String id) {
        return jpa.findById(id);
    }

    public List<AuditLog> findAll() {
        return jpa.findAllByOrderByTimestampDesc();
    }

    public List<AuditLog> findByContainerId(String containerId) {
        return jpa.findByContainerIdOrderByTimestampDesc(containerId);
    }

    public List<AuditLog> findByPlaybookName(String playbookName) {
        return jpa.findByPlaybookNameOrderByTimestampDesc(playbookName);
    }

    public List<AuditLog> findByExecutionResult(ExecutionResult result) {
        return jpa.findByExecutionResultOrderByTimestampDesc(result);
    }

    public List<AuditLog> findByTimeRange(LocalDateTime from, LocalDateTime to) {
        return jpa.findByTimestampBetweenOrderByTimestampDesc(from, to);
    }

    public List<AuditLog> findRecent(int limit) {
        return jpa.findAllByOrderByTimestampDesc(PageRequest.of(0, limit));
    }

    public long count() {
        return jpa.count();
    }

    public List<AuditLog> findOlderThan(LocalDateTime cutoff) {
        return jpa.findByTimestampBefore(cutoff);
    }

    public int deleteOlderThan(LocalDateTime cutoff) {
        List<AuditLog> toDelete = jpa.findByTimestampBefore(cutoff);
        jpa.deleteAll(toDelete);
        return toDelete.size();
    }

    public void clear() {
        jpa.deleteAll();
    }
}
