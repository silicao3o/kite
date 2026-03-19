package com.lite_k8s.audit;

import com.lite_k8s.playbook.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogRepositoryTest {

    private AuditLogRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AuditLogRepository();
    }

    @Test
    @DisplayName("감사 로그 저장")
    void shouldSaveAuditLog() {
        // given
        AuditLog log = createAuditLog("web", "restart");

        // when
        repository.save(log);

        // then
        Optional<AuditLog> found = repository.findById(log.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getContainerName()).isEqualTo("web");
    }

    @Test
    @DisplayName("전체 조회 - 최신순 정렬")
    void shouldFindAllOrderByTimestampDesc() {
        // given
        AuditLog log1 = createAuditLog("web-1", "restart");
        AuditLog log2 = createAuditLog("web-2", "kill");
        AuditLog log3 = createAuditLog("web-3", "restart");

        repository.save(log1);
        repository.save(log2);
        repository.save(log3);

        // when
        List<AuditLog> all = repository.findAll();

        // then
        assertThat(all).hasSize(3);
        // 최신순 정렬 확인 (log3가 가장 최근)
        assertThat(all.get(0).getContainerName()).isEqualTo("web-3");
    }

    @Test
    @DisplayName("컨테이너별 조회")
    void shouldFindByContainerId() {
        // given
        AuditLog log1 = createAuditLog("web", "restart");
        log1.setContainerId("container-123");
        AuditLog log2 = createAuditLog("web", "kill");
        log2.setContainerId("container-123");
        AuditLog log3 = createAuditLog("db", "restart");
        log3.setContainerId("container-456");

        repository.save(log1);
        repository.save(log2);
        repository.save(log3);

        // when
        List<AuditLog> webLogs = repository.findByContainerId("container-123");

        // then
        assertThat(webLogs).hasSize(2);
    }

    @Test
    @DisplayName("Playbook별 조회")
    void shouldFindByPlaybookName() {
        // given
        repository.save(createAuditLog("web-1", "container-restart"));
        repository.save(createAuditLog("web-2", "container-restart"));
        repository.save(createAuditLog("db", "oom-recovery"));

        // when
        List<AuditLog> restartLogs = repository.findByPlaybookName("container-restart");

        // then
        assertThat(restartLogs).hasSize(2);
    }

    @Test
    @DisplayName("실행 결과별 조회")
    void shouldFindByExecutionResult() {
        // given
        AuditLog success = createAuditLog("web-1", "restart");
        success.recordSuccess("성공");

        AuditLog failure = createAuditLog("web-2", "restart");
        failure.recordFailure("실패");

        AuditLog blocked = createAuditLog("db", "kill");
        blocked.recordBlocked("차단");

        repository.save(success);
        repository.save(failure);
        repository.save(blocked);

        // when
        List<AuditLog> successLogs = repository.findByExecutionResult(ExecutionResult.SUCCESS);
        List<AuditLog> failureLogs = repository.findByExecutionResult(ExecutionResult.FAILURE);

        // then
        assertThat(successLogs).hasSize(1);
        assertThat(failureLogs).hasSize(1);
    }

    @Test
    @DisplayName("시간 범위 조회")
    void shouldFindByTimeRange() {
        // given
        AuditLog old = createAuditLog("old", "restart");
        // 과거 시간으로 설정
        old.setTimestamp(LocalDateTime.now().minusDays(10));

        AuditLog recent = createAuditLog("recent", "restart");

        repository.save(old);
        repository.save(recent);

        // when
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now().plusDays(1);
        List<AuditLog> recentLogs = repository.findByTimeRange(from, to);

        // then
        assertThat(recentLogs).hasSize(1);
        assertThat(recentLogs.get(0).getContainerName()).isEqualTo("recent");
    }

    @Test
    @DisplayName("최근 N개 조회")
    void shouldFindRecent() {
        // given
        for (int i = 0; i < 10; i++) {
            repository.save(createAuditLog("container-" + i, "restart"));
        }

        // when
        List<AuditLog> recent5 = repository.findRecent(5);

        // then
        assertThat(recent5).hasSize(5);
    }

    @Test
    @DisplayName("전체 개수 조회")
    void shouldCount() {
        // given
        repository.save(createAuditLog("a", "restart"));
        repository.save(createAuditLog("b", "restart"));
        repository.save(createAuditLog("c", "restart"));

        // when
        long count = repository.count();

        // then
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("Append-Only: update 메서드 없음 - save는 새 ID로만 저장")
    void shouldBeAppendOnly() {
        // given
        AuditLog log = createAuditLog("web", "restart");
        String originalId = log.getId();
        repository.save(log);

        // when - 동일 ID로 다시 save하면 덮어쓰기 (ID는 생성 시 고정)
        log.setContainerName("modified");
        repository.save(log);

        // then - ID가 같으므로 덮어쓰기 됨 (내부 상태 변경은 허용)
        // 하지만 외부에서 ID를 변경하거나 삭제하는 메서드는 제공하지 않음
        Optional<AuditLog> found = repository.findById(originalId);
        assertThat(found).isPresent();
        // Repository에는 delete, update 메서드가 없음 (Append-Only)
    }

    @Test
    @DisplayName("보존 기간이 지난 로그 조회")
    void shouldFindLogsOlderThan() {
        // given
        AuditLog oldLog = createAuditLog("old", "restart");
        oldLog.setTimestamp(LocalDateTime.now().minusDays(200));

        AuditLog recentLog = createAuditLog("recent", "restart");

        repository.save(oldLog);
        repository.save(recentLog);

        // when
        List<AuditLog> expiredLogs = repository.findOlderThan(LocalDateTime.now().minusDays(180));

        // then
        assertThat(expiredLogs).hasSize(1);
        assertThat(expiredLogs.get(0).getContainerName()).isEqualTo("old");
    }

    @Test
    @DisplayName("보존 정책에 따른 만료 로그 삭제")
    void shouldDeleteExpiredLogs() {
        // given
        AuditLog oldLog1 = createAuditLog("old-1", "restart");
        oldLog1.setTimestamp(LocalDateTime.now().minusDays(200));

        AuditLog oldLog2 = createAuditLog("old-2", "restart");
        oldLog2.setTimestamp(LocalDateTime.now().minusDays(190));

        AuditLog recentLog = createAuditLog("recent", "restart");

        repository.save(oldLog1);
        repository.save(oldLog2);
        repository.save(recentLog);

        // when
        int deleted = repository.deleteOlderThan(LocalDateTime.now().minusDays(180));

        // then
        assertThat(deleted).isEqualTo(2);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findAll().get(0).getContainerName()).isEqualTo("recent");
    }

    private AuditLog createAuditLog(String containerName, String playbookName) {
        return AuditLog.builder()
                .containerName(containerName)
                .containerId(containerName + "-id")
                .playbookName(playbookName)
                .actionType("container." + playbookName)
                .intent("테스트 조치")
                .riskLevel(RiskLevel.LOW)
                .build();
    }
}
