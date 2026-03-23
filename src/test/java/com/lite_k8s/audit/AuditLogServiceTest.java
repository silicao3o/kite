package com.lite_k8s.audit;

import com.lite_k8s.playbook.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogServiceTest {

    @Mock
    private AuditLogJpaRepository mockJpa;

    private AuditLogService auditLogService;
    private AuditLogRepository repository;

    // In-memory store to simulate JPA behavior
    private final List<AuditLog> store = new ArrayList<>();

    @BeforeEach
    void setUp() {
        store.clear();

        // save() 호출 시 store에 저장
        when(mockJpa.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog log = inv.getArgument(0);
            store.removeIf(l -> l.getId().equals(log.getId()));
            store.add(log);
            return log;
        });

        // findById() 호출 시 store에서 조회
        when(mockJpa.findById(any(String.class))).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return store.stream().filter(l -> l.getId().equals(id)).findFirst();
        });

        // findAllByOrderByTimestampDesc() 호출 시 store 반환
        when(mockJpa.findAllByOrderByTimestampDesc()).thenAnswer(inv ->
                store.stream()
                        .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                        .toList()
        );

        // findAllByOrderByTimestampDesc(Pageable) 호출 시 store에서 limit 적용
        when(mockJpa.findAllByOrderByTimestampDesc(any(Pageable.class))).thenAnswer(inv -> {
            Pageable pageable = inv.getArgument(0);
            return store.stream()
                    .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                    .limit(pageable.getPageSize())
                    .toList();
        });

        // findByContainerIdOrderByTimestampDesc() 호출 시 store에서 필터링
        when(mockJpa.findByContainerIdOrderByTimestampDesc(any(String.class))).thenAnswer(inv -> {
            String containerId = inv.getArgument(0);
            return store.stream()
                    .filter(l -> l.getContainerId().equals(containerId))
                    .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                    .toList();
        });

        // findByExecutionResultOrderByTimestampDesc() 호출 시 store에서 필터링
        when(mockJpa.findByExecutionResultOrderByTimestampDesc(any(ExecutionResult.class))).thenAnswer(inv -> {
            ExecutionResult result = inv.getArgument(0);
            return store.stream()
                    .filter(l -> l.getExecutionResult() == result)
                    .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                    .toList();
        });

        // count() 호출 시 store 크기 반환
        when(mockJpa.count()).thenAnswer(inv -> (long) store.size());

        repository = new AuditLogRepository(mockJpa);
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
                RiskLevel.MEDIUM
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
                "재시작", null, RiskLevel.LOW
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
                "재시작", null, RiskLevel.LOW
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
                "강제 종료", null, RiskLevel.CRITICAL
        );

        // when
        auditLogService.logActionBlocked(log.getId(), "CRITICAL 위험도 - 수동 승인 필요");

        // then
        AuditLog updated = repository.findById(log.getId()).get();
        assertThat(updated.getExecutionResult()).isEqualTo(ExecutionResult.BLOCKED);
    }

    @Test
    @DisplayName("최근 로그 조회")
    void shouldGetRecentLogs() {
        // given
        for (int i = 0; i < 20; i++) {
            auditLogService.logActionStart(
                    "container-" + i, "id-" + i, "restart", "container.restart",
                    "재시작", null, RiskLevel.LOW
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
        auditLogService.logActionStart("web", "container-web", "restart", "r", "i", null, RiskLevel.LOW);
        auditLogService.logActionStart("web", "container-web", "kill", "k", "i", null, RiskLevel.LOW);
        auditLogService.logActionStart("db", "container-db", "restart", "r", "i", null, RiskLevel.LOW);

        // when
        List<AuditLog> webLogs = auditLogService.getLogsByContainerId("container-web");

        // then
        assertThat(webLogs).hasSize(2);
    }

    @Test
    @DisplayName("통계 조회")
    void shouldGetStatistics() {
        // given
        AuditLog success1 = auditLogService.logActionStart("a", "1", "r", "t", "i", null, RiskLevel.LOW);
        auditLogService.logActionSuccess(success1.getId(), "ok");

        AuditLog success2 = auditLogService.logActionStart("b", "2", "r", "t", "i", null, RiskLevel.LOW);
        auditLogService.logActionSuccess(success2.getId(), "ok");

        AuditLog failure = auditLogService.logActionStart("c", "3", "r", "t", "i", null, RiskLevel.LOW);
        auditLogService.logActionFailure(failure.getId(), "error");

        AuditLog blocked = auditLogService.logActionStart("d", "4", "r", "t", "i", null, RiskLevel.CRITICAL);
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
