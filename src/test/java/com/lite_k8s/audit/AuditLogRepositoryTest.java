package com.lite_k8s.audit;

import com.lite_k8s.playbook.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AuditLogRepositoryTest {

    @Autowired
    private AuditLogJpaRepository repository;

    @Test
    @DisplayName("감사 로그 저장")
    void shouldSaveAuditLog() {
        AuditLog log = createAuditLog("web", "restart");
        repository.save(log);

        Optional<AuditLog> found = repository.findById(log.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getContainerName()).isEqualTo("web");
    }

    @Test
    @DisplayName("전체 조회 - 최신순 정렬")
    void shouldFindAllOrderByTimestampDesc() {
        AuditLog log1 = createAuditLogWithTime("web-1", "restart", LocalDateTime.now().minusSeconds(2));
        AuditLog log2 = createAuditLogWithTime("web-2", "kill", LocalDateTime.now().minusSeconds(1));
        AuditLog log3 = createAuditLogWithTime("web-3", "restart", LocalDateTime.now());

        repository.save(log1);
        repository.save(log2);
        repository.save(log3);

        List<AuditLog> all = repository.findAllByOrderByTimestampDesc();
        assertThat(all).hasSize(3);
        assertThat(all.get(0).getContainerName()).isEqualTo("web-3");
    }

    @Test
    @DisplayName("컨테이너별 조회")
    void shouldFindByContainerId() {
        AuditLog log1 = createAuditLog("web", "restart");
        log1.setContainerId("container-123");
        AuditLog log2 = createAuditLog("web", "kill");
        log2.setContainerId("container-123");
        AuditLog log3 = createAuditLog("db", "restart");
        log3.setContainerId("container-456");

        repository.save(log1);
        repository.save(log2);
        repository.save(log3);

        List<AuditLog> webLogs = repository.findByContainerIdOrderByTimestampDesc("container-123");
        assertThat(webLogs).hasSize(2);
    }

    @Test
    @DisplayName("Playbook별 조회")
    void shouldFindByPlaybookName() {
        repository.save(createAuditLog("web-1", "container-restart"));
        repository.save(createAuditLog("web-2", "container-restart"));
        repository.save(createAuditLog("db", "oom-recovery"));

        List<AuditLog> restartLogs = repository.findByPlaybookNameOrderByTimestampDesc("container-restart");
        assertThat(restartLogs).hasSize(2);
    }

    @Test
    @DisplayName("실행 결과별 조회")
    void shouldFindByExecutionResult() {
        AuditLog success = createAuditLog("web-1", "restart");
        success.recordSuccess("성공");
        AuditLog failure = createAuditLog("web-2", "restart");
        failure.recordFailure("실패");

        repository.save(success);
        repository.save(failure);

        assertThat(repository.findByExecutionResultOrderByTimestampDesc(ExecutionResult.SUCCESS)).hasSize(1);
        assertThat(repository.findByExecutionResultOrderByTimestampDesc(ExecutionResult.FAILURE)).hasSize(1);
    }

    @Test
    @DisplayName("시간 범위 조회")
    void shouldFindByTimeRange() {
        AuditLog old = createAuditLogWithTime("old", "restart", LocalDateTime.now().minusDays(10));
        AuditLog recent = createAuditLog("recent", "restart");

        repository.save(old);
        repository.save(recent);

        List<AuditLog> recentLogs = repository.findByTimestampBetweenOrderByTimestampDesc(
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
        assertThat(recentLogs).hasSize(1);
        assertThat(recentLogs.get(0).getContainerName()).isEqualTo("recent");
    }

    @Test
    @DisplayName("최근 N개 조회")
    void shouldFindRecent() {
        for (int i = 0; i < 10; i++) {
            repository.save(createAuditLog("container-" + i, "restart"));
        }

        List<AuditLog> recent5 = repository.findAllByOrderByTimestampDesc(PageRequest.of(0, 5));
        assertThat(recent5).hasSize(5);
    }

    @Test
    @DisplayName("전체 개수 조회")
    void shouldCount() {
        repository.save(createAuditLog("a", "restart"));
        repository.save(createAuditLog("b", "restart"));
        repository.save(createAuditLog("c", "restart"));

        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("보존 정책에 따른 만료 로그 삭제")
    void shouldDeleteExpiredLogs() {
        AuditLog oldLog1 = createAuditLogWithTime("old-1", "restart", LocalDateTime.now().minusDays(200));
        AuditLog oldLog2 = createAuditLogWithTime("old-2", "restart", LocalDateTime.now().minusDays(190));
        AuditLog recentLog = createAuditLog("recent", "restart");

        repository.save(oldLog1);
        repository.save(oldLog2);
        repository.save(recentLog);

        List<AuditLog> toDelete = repository.findByTimestampBefore(LocalDateTime.now().minusDays(180));
        repository.deleteAll(toDelete);

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findAllByOrderByTimestampDesc().get(0).getContainerName()).isEqualTo("recent");
    }

    private AuditLog createAuditLog(String containerName, String playbookName) {
        return createAuditLogWithTime(containerName, playbookName, LocalDateTime.now());
    }

    private AuditLog createAuditLogWithTime(String containerName, String playbookName, LocalDateTime time) {
        AuditLog log = AuditLog.builder()
                .containerName(containerName)
                .containerId(containerName + "-id")
                .playbookName(playbookName)
                .actionType("container." + playbookName)
                .intent("테스트 조치")
                .riskLevel(RiskLevel.LOW)
                .build();
        log.setTimestamp(time);
        return log;
    }
}
