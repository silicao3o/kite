package com.lite_k8s.audit;

import com.lite_k8s.playbook.RiskLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AI 조치 감사 로그 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;

    /**
     * 조치 시작 로그 기록
     */
    public AuditLog logActionStart(String containerName, String containerId,
                                    String playbookName, String actionType,
                                    String intent, String reasoning,
                                    RiskLevel riskLevel, boolean approvalRequired) {
        AuditLog auditLog = AuditLog.builder()
                .containerName(containerName)
                .containerId(containerId)
                .playbookName(playbookName)
                .actionType(actionType)
                .intent(intent)
                .reasoning(reasoning)
                .riskLevel(riskLevel)
                .approvalRequired(approvalRequired)
                .build();

        repository.save(auditLog);

        log.info("Audit log started - ID: {}, Playbook: {}, Container: {}, Risk: {}",
                auditLog.getId(), playbookName, containerName, riskLevel);

        return auditLog;
    }

    /**
     * 조치 성공 기록
     */
    public void logActionSuccess(String logId, String message) {
        findAndUpdate(logId, auditLog -> {
            auditLog.recordSuccess(message);
            log.info("Audit log success - ID: {}, Message: {}", logId, message);
        });
    }

    /**
     * 조치 실패 기록
     */
    public void logActionFailure(String logId, String message) {
        findAndUpdate(logId, auditLog -> {
            auditLog.recordFailure(message);
            log.warn("Audit log failure - ID: {}, Message: {}", logId, message);
        });
    }

    /**
     * 조치 차단 기록
     */
    public void logActionBlocked(String logId, String reason) {
        findAndUpdate(logId, auditLog -> {
            auditLog.recordBlocked(reason);
            log.info("Audit log blocked - ID: {}, Reason: {}", logId, reason);
        });
    }

    /**
     * 승인 정보 기록
     */
    public void logApproval(String logId, String approver, boolean approved) {
        findAndUpdate(logId, auditLog -> {
            auditLog.recordApproval(approver, approved);
            log.info("Audit log approval - ID: {}, Approver: {}, Approved: {}",
                    logId, approver, approved);
        });
    }

    /**
     * 타임아웃 기록
     */
    public void logTimeout(String logId) {
        findAndUpdate(logId, auditLog -> {
            auditLog.recordTimeout();
            log.info("Audit log timeout - ID: {}", logId);
        });
    }

    private void findAndUpdate(String logId, java.util.function.Consumer<AuditLog> updater) {
        Optional<AuditLog> optional = repository.findById(logId);
        if (optional.isPresent()) {
            AuditLog auditLog = optional.get();
            updater.accept(auditLog);
            repository.save(auditLog);
        } else {
            log.warn("Audit log not found: {}", logId);
        }
    }

    /**
     * 최근 로그 조회
     */
    public List<AuditLog> getRecentLogs(int limit) {
        return repository.findRecent(limit);
    }

    /**
     * 컨테이너별 로그 조회
     */
    public List<AuditLog> getLogsByContainerId(String containerId) {
        return repository.findByContainerId(containerId);
    }

    /**
     * Playbook별 로그 조회
     */
    public List<AuditLog> getLogsByPlaybookName(String playbookName) {
        return repository.findByPlaybookName(playbookName);
    }

    /**
     * 시간 범위 로그 조회
     */
    public List<AuditLog> getLogsByTimeRange(LocalDateTime from, LocalDateTime to) {
        return repository.findByTimeRange(from, to);
    }

    /**
     * 전체 로그 조회
     */
    public List<AuditLog> getAllLogs() {
        return repository.findAll();
    }

    /**
     * ID로 조회
     */
    public Optional<AuditLog> findById(String id) {
        return repository.findById(id);
    }

    /**
     * 통계 조회
     */
    public AuditStatistics getStatistics() {
        List<AuditLog> all = repository.findAll();

        if (all.isEmpty()) {
            return AuditStatistics.empty();
        }

        long totalCount = all.size();
        long successCount = all.stream()
                .filter(log -> log.getExecutionResult() == ExecutionResult.SUCCESS)
                .count();
        long failureCount = all.stream()
                .filter(log -> log.getExecutionResult() == ExecutionResult.FAILURE)
                .count();
        long blockedCount = all.stream()
                .filter(log -> log.getExecutionResult() == ExecutionResult.BLOCKED)
                .count();
        long timeoutCount = all.stream()
                .filter(log -> log.getExecutionResult() == ExecutionResult.TIMEOUT)
                .count();
        long pendingCount = all.stream()
                .filter(log -> log.getExecutionResult() == ExecutionResult.PENDING)
                .count();

        // 성공률: 성공 / (성공 + 실패) * 100
        long completedCount = successCount + failureCount;
        double successRate = completedCount > 0
                ? (double) successCount / completedCount * 100
                : 0.0;

        return AuditStatistics.builder()
                .totalCount(totalCount)
                .successCount(successCount)
                .failureCount(failureCount)
                .blockedCount(blockedCount)
                .timeoutCount(timeoutCount)
                .pendingCount(pendingCount)
                .successRate(successRate)
                .build();
    }
}
