package com.lite_k8s.audit;

import com.lite_k8s.playbook.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogTest {

    @Test
    @DisplayName("AuditLog 생성 시 기본값 설정")
    void shouldSetDefaultValuesOnCreation() {
        // when
        AuditLog log = AuditLog.builder()
                .containerName("web-server")
                .containerId("abc123")
                .playbookName("container-restart")
                .actionType("container.restart")
                .intent("컨테이너 비정상 종료로 인한 자동 재시작")
                .riskLevel(RiskLevel.MEDIUM)
                .build();

        // then
        assertThat(log.getId()).isNotNull();
        assertThat(log.getTimestamp()).isNotNull();
        assertThat(log.getContainerName()).isEqualTo("web-server");
        assertThat(log.getActionType()).isEqualTo("container.restart");
        assertThat(log.getExecutionResult()).isEqualTo(ExecutionResult.PENDING);
    }

    @Test
    @DisplayName("조치 성공 기록")
    void shouldRecordSuccess() {
        // given
        AuditLog log = AuditLog.builder()
                .containerName("web")
                .containerId("123")
                .playbookName("restart")
                .actionType("container.restart")
                .intent("재시작")
                .riskLevel(RiskLevel.LOW)
                .build();

        // when
        log.recordSuccess("컨테이너가 정상적으로 재시작됨");

        // then
        assertThat(log.getExecutionResult()).isEqualTo(ExecutionResult.SUCCESS);
        assertThat(log.getResultMessage()).isEqualTo("컨테이너가 정상적으로 재시작됨");
        assertThat(log.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("조치 실패 기록")
    void shouldRecordFailure() {
        // given
        AuditLog log = AuditLog.builder()
                .containerName("web")
                .containerId("123")
                .playbookName("restart")
                .actionType("container.restart")
                .intent("재시작")
                .riskLevel(RiskLevel.LOW)
                .build();

        // when
        log.recordFailure("Docker API 연결 실패");

        // then
        assertThat(log.getExecutionResult()).isEqualTo(ExecutionResult.FAILURE);
        assertThat(log.getResultMessage()).isEqualTo("Docker API 연결 실패");
    }

    @Test
    @DisplayName("조치 차단 기록")
    void shouldRecordBlocked() {
        // given
        AuditLog log = AuditLog.builder()
                .containerName("db")
                .containerId("456")
                .playbookName("force-kill")
                .actionType("container.kill")
                .intent("강제 종료")
                .riskLevel(RiskLevel.CRITICAL)
                .build();

        // when
        log.recordBlocked("CRITICAL 위험도 조치 - 수동 승인 필요");

        // then
        assertThat(log.getExecutionResult()).isEqualTo(ExecutionResult.BLOCKED);
        assertThat(log.getResultMessage()).contains("수동 승인");
    }

    @Test
    @DisplayName("AI 판단 이유 저장")
    void shouldStoreReasoning() {
        // given
        String reasoning = """
            컨테이너가 OOM으로 종료됨.
            메모리 사용량이 limit의 95%를 초과했음.
            최근 24시간 내 동일 원인으로 2회 종료됨.
            메모리 limit 상향 조정을 권장함.
            """;

        // when
        AuditLog log = AuditLog.builder()
                .containerName("worker")
                .containerId("789")
                .playbookName("oom-recovery")
                .actionType("container.restart")
                .intent("OOM 복구를 위한 재시작")
                .reasoning(reasoning)
                .riskLevel(RiskLevel.MEDIUM)
                .build();

        // then
        assertThat(log.getReasoning()).contains("OOM");
        assertThat(log.getReasoning()).contains("메모리 limit 상향");
    }

    @Test
    @DisplayName("승인 정보 기록")
    void shouldRecordApprovalInfo() {
        // given
        AuditLog log = AuditLog.builder()
                .containerName("db")
                .containerId("456")
                .playbookName("restart")
                .actionType("container.restart")
                .intent("DB 재시작")
                .riskLevel(RiskLevel.HIGH)
                .approvalRequired(true)
                .build();

        // when
        log.recordApproval("admin", true);

        // then
        assertThat(log.isApprovalRequired()).isTrue();
        assertThat(log.getApprovedBy()).isEqualTo("admin");
        assertThat(log.isApproved()).isTrue();
    }

    @Test
    @DisplayName("타임아웃 기록")
    void shouldRecordTimeout() {
        // given
        AuditLog log = AuditLog.builder()
                .containerName("web")
                .containerId("123")
                .playbookName("restart")
                .actionType("container.restart")
                .intent("재시작")
                .riskLevel(RiskLevel.HIGH)
                .approvalRequired(true)
                .build();

        // when
        log.recordTimeout();

        // then
        assertThat(log.getExecutionResult()).isEqualTo(ExecutionResult.TIMEOUT);
        assertThat(log.getResultMessage()).contains("타임아웃");
    }
}
