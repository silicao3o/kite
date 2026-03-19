package com.lite_k8s.approval;

import com.lite_k8s.playbook.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PendingApprovalRepositoryTest {

    private PendingApprovalRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PendingApprovalRepository();
    }

    @Test
    @DisplayName("승인 요청 저장")
    void shouldSaveApproval() {
        // given
        PendingApproval approval = PendingApproval.create(
                "restart", "web-server", RiskLevel.HIGH
        );

        // when
        repository.save(approval);

        // then
        Optional<PendingApproval> found = repository.findById(approval.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getPlaybookName()).isEqualTo("restart");
    }

    @Test
    @DisplayName("대기 중인 승인 목록 조회")
    void shouldFindPendingApprovals() {
        // given
        PendingApproval pending1 = PendingApproval.create("restart", "web", RiskLevel.HIGH);
        PendingApproval pending2 = PendingApproval.create("kill", "db", RiskLevel.CRITICAL);
        PendingApproval approved = PendingApproval.create("notify", "worker", RiskLevel.LOW);
        approved.approve("admin");

        repository.save(pending1);
        repository.save(pending2);
        repository.save(approved);

        // when
        List<PendingApproval> pendingList = repository.findByStatus(ApprovalStatus.PENDING);

        // then
        assertThat(pendingList).hasSize(2);
        assertThat(pendingList).extracting(PendingApproval::getPlaybookName)
                .containsExactlyInAnyOrder("restart", "kill");
    }

    @Test
    @DisplayName("모든 승인 요청 조회")
    void shouldFindAllApprovals() {
        // given
        repository.save(PendingApproval.create("a", "c1", RiskLevel.LOW));
        repository.save(PendingApproval.create("b", "c2", RiskLevel.MEDIUM));
        repository.save(PendingApproval.create("c", "c3", RiskLevel.HIGH));

        // when
        List<PendingApproval> all = repository.findAll();

        // then
        assertThat(all).hasSize(3);
    }

    @Test
    @DisplayName("ID로 조회 - 없는 경우")
    void shouldReturnEmptyWhenNotFound() {
        // when
        Optional<PendingApproval> found = repository.findById("non-existent");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("만료된 승인 요청 조회")
    void shouldFindExpiredApprovals() {
        // given
        PendingApproval expired = PendingApproval.create("restart", "web", RiskLevel.HIGH);
        expired.setExpiresAt(expired.getRequestedAt().minusMinutes(1)); // 이미 만료됨

        PendingApproval valid = PendingApproval.create("kill", "db", RiskLevel.CRITICAL);

        repository.save(expired);
        repository.save(valid);

        // when
        List<PendingApproval> expiredList = repository.findExpiredPending();

        // then
        assertThat(expiredList).hasSize(1);
        assertThat(expiredList.get(0).getPlaybookName()).isEqualTo("restart");
    }

    @Test
    @DisplayName("삭제")
    void shouldDelete() {
        // given
        PendingApproval approval = PendingApproval.create("restart", "web", RiskLevel.HIGH);
        repository.save(approval);

        // when
        repository.delete(approval.getId());

        // then
        assertThat(repository.findById(approval.getId())).isEmpty();
    }
}
