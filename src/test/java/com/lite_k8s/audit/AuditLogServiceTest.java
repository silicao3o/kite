package com.lite_k8s.audit;

import com.lite_k8s.playbook.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogServiceTest {

    private AuditLogService auditLogService;
    private AuditLogRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AuditLogRepository();
        auditLogService = new AuditLogService(repository);
    }

    @Test
    @DisplayName("조치 시작 로그 기록")
    void shouldLogActionStart() {
        // when
        AuditLog log = auditLogService.logActionStart(
                "web-server",
                "container-123",
                "container-restart",
                "container.restart",
                "컨테이너 비정상 종료로 인한 자동 재시작",
                "Exit code 137로 종료됨. OOM Killer에 의한 것으로 추정.",
                RiskLevel.MEDIUM,
                false
        );

        // then
        assertThat(log.getId()).isNotNull();
        assertThat(log.getContainerName()).isEqualTo("web-server");
        assertThat(log.getIntent()).contains("자동 재시작");
        assertThat(log.getReasoning()).contains("OOM Killer");
        assertThat(log.getExecutionResult()).isEqualTo(ExecutionResult.PENDING);
        assertThat(repository.findById(log.getId())).isPresent();
    }

    @Test
    @DisplayName("조치 성공 기록")
    void shouldLogActionSuccess() {
        // given
        AuditLog log = auditLogService.logActionStart(
                "web", "123", "restart", "container.restart",
                "재시작", null, RiskLevel.LOW, false
        );

        // when
        auditLogService.logActionSuccess(log.getId(), "컨테이너가 정상적으로 재시작됨");

        // then
        AuditLog updated = repository.findById(log.getId()).get();
        assertThat(updated.getExecutionResult()).isEqualTo(ExecutionResult.SUCCESS);
        assertThat(updated.getResultMessage()).contains("정상적으로");
    }

    @Test
    @DisplayName("조치 실패 기록")
    void shouldLogActionFailure() {
        // given
        AuditLog log = auditLogService.logActionStart(
                "web", "123", "restart", "container.restart",
                "재시작", null, RiskLevel.LOW, false
        );

        // when
        auditLogService.logActionFailure(log.getId(), "Docker API 연결 실패");

        // then
        AuditLog updated = repository.findById(log.getId()).get();
        assertThat(updated.getExecutionResult()).isEqualTo(ExecutionResult.FAILURE);
        assertThat(updated.getResultMessage()).contains("Docker API");
    }

    @Test
    @DisplayName("조치 차단 기록")
    void shouldLogActionBlocked() {
        // given
        AuditLog log = auditLogService.logActionStart(
                "db", "456", "force-kill", "container.kill",
                "강제 종료", null, RiskLevel.CRITICAL, true
        );

        // when
        auditLogService.logActionBlocked(log.getId(), "CRITICAL 위험도 - 수동 승인 필요");

        // then
        AuditLog updated = repository.findById(log.getId()).get();
        assertThat(updated.getExecutionResult()).isEqualTo(ExecutionResult.BLOCKED);
    }

    @Test
    @DisplayName("승인 정보 기록")
    void shouldLogApproval() {
        // given
        AuditLog log = auditLogService.logActionStart(
                "db", "456", "restart", "container.restart",
                "재시작", null, RiskLevel.HIGH, true
        );

        // when
        auditLogService.logApproval(log.getId(), "admin", true);

        // then
        AuditLog updated = repository.findById(log.getId()).get();
        assertThat(updated.getApprovedBy()).isEqualTo("admin");
        assertThat(updated.isApproved()).isTrue();
    }

    @Test
    @DisplayName("타임아웃 기록")
    void shouldLogTimeout() {
        // given
        AuditLog log = auditLogService.logActionStart(
                "web", "123", "restart", "container.restart",
                "재시작", null, RiskLevel.HIGH, true
        );

        // when
        auditLogService.logTimeout(log.getId());

        // then
        AuditLog updated = repository.findById(log.getId()).get();
        assertThat(updated.getExecutionResult()).isEqualTo(ExecutionResult.TIMEOUT);
    }

    @Test
    @DisplayName("최근 로그 조회")
    void shouldGetRecentLogs() {
        // given
        for (int i = 0; i < 20; i++) {
            auditLogService.logActionStart(
                    "container-" + i, "id-" + i, "restart", "container.restart",
                    "재시작", null, RiskLevel.LOW, false
            );
        }

        // when
        List<AuditLog> recent = auditLogService.getRecentLogs(10);

        // then
        assertThat(recent).hasSize(10);
    }

    @Test
    @DisplayName("컨테이너별 로그 조회")
    void shouldGetLogsByContainer() {
        // given
        auditLogService.logActionStart("web", "container-web", "restart", "r", "i", null, RiskLevel.LOW, false);
        auditLogService.logActionStart("web", "container-web", "kill", "k", "i", null, RiskLevel.LOW, false);
        auditLogService.logActionStart("db", "container-db", "restart", "r", "i", null, RiskLevel.LOW, false);

        // when
        List<AuditLog> webLogs = auditLogService.getLogsByContainerId("container-web");

        // then
        assertThat(webLogs).hasSize(2);
    }

    @Test
    @DisplayName("통계 조회")
    void shouldGetStatistics() {
        // given
        AuditLog success1 = auditLogService.logActionStart("a", "1", "r", "t", "i", null, RiskLevel.LOW, false);
        auditLogService.logActionSuccess(success1.getId(), "ok");

        AuditLog success2 = auditLogService.logActionStart("b", "2", "r", "t", "i", null, RiskLevel.LOW, false);
        auditLogService.logActionSuccess(success2.getId(), "ok");

        AuditLog failure = auditLogService.logActionStart("c", "3", "r", "t", "i", null, RiskLevel.LOW, false);
        auditLogService.logActionFailure(failure.getId(), "error");

        AuditLog blocked = auditLogService.logActionStart("d", "4", "r", "t", "i", null, RiskLevel.CRITICAL, true);
        auditLogService.logActionBlocked(blocked.getId(), "blocked");

        // when
        AuditStatistics stats = auditLogService.getStatistics();

        // then
        assertThat(stats.getTotalCount()).isEqualTo(4);
        assertThat(stats.getSuccessCount()).isEqualTo(2);
        assertThat(stats.getFailureCount()).isEqualTo(1);
        assertThat(stats.getBlockedCount()).isEqualTo(1);
        // 성공률: 2 / (2 + 1) * 100 = 66.67%
        assertThat(stats.getSuccessRate()).isGreaterThan(66.0).isLessThan(67.0);
    }
}
