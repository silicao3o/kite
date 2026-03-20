package com.lite_k8s.approval;

import com.lite_k8s.playbook.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PendingApprovalRepositoryTest {

    @Autowired
    private PendingApprovalJpaRepository repository;

    @Test
    @DisplayName("승인 요청 저장")
    void shouldSaveApproval() {
        PendingApproval approval = PendingApproval.create("restart", "web-server", RiskLevel.HIGH);
        repository.save(approval);

        Optional<PendingApproval> found = repository.findById(approval.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getPlaybookName()).isEqualTo("restart");
    }

    @Test
    @DisplayName("대기 중인 승인 목록 조회")
    void shouldFindPendingApprovals() {
        PendingApproval pending1 = PendingApproval.create("restart", "web", RiskLevel.HIGH);
        PendingApproval pending2 = PendingApproval.create("kill", "db", RiskLevel.CRITICAL);
        PendingApproval approved = PendingApproval.create("notify", "worker", RiskLevel.LOW);
        approved.approve("admin");

        repository.save(pending1);
        repository.save(pending2);
        repository.save(approved);

        List<PendingApproval> pendingList = repository.findByStatus(ApprovalStatus.PENDING);
        assertThat(pendingList).hasSize(2);
        assertThat(pendingList).extracting(PendingApproval::getPlaybookName)
                .containsExactlyInAnyOrder("restart", "kill");
    }

    @Test
    @DisplayName("모든 승인 요청 조회")
    void shouldFindAllApprovals() {
        repository.save(PendingApproval.create("a", "c1", RiskLevel.LOW));
        repository.save(PendingApproval.create("b", "c2", RiskLevel.MEDIUM));
        repository.save(PendingApproval.create("c", "c3", RiskLevel.HIGH));

        assertThat(repository.findAll()).hasSize(3);
    }

    @Test
    @DisplayName("ID로 조회 - 없는 경우")
    void shouldReturnEmptyWhenNotFound() {
        assertThat(repository.findById("non-existent")).isEmpty();
    }

    @Test
    @DisplayName("만료된 승인 요청 조회")
    void shouldFindExpiredApprovals() {
        PendingApproval expired = PendingApproval.create("restart", "web", RiskLevel.HIGH);
        expired.setExpiresAt(expired.getRequestedAt().minusMinutes(1));

        PendingApproval valid = PendingApproval.create("kill", "db", RiskLevel.CRITICAL);

        repository.save(expired);
        repository.save(valid);

        List<PendingApproval> expiredList = repository.findByStatusAndExpiresAtBefore(
                ApprovalStatus.PENDING, java.time.LocalDateTime.now());
        assertThat(expiredList).hasSize(1);
        assertThat(expiredList.get(0).getPlaybookName()).isEqualTo("restart");
    }

    @Test
    @DisplayName("삭제")
    void shouldDelete() {
        PendingApproval approval = PendingApproval.create("restart", "web", RiskLevel.HIGH);
        repository.save(approval);

        repository.deleteById(approval.getId());

        assertThat(repository.findById(approval.getId())).isEmpty();
    }
}
