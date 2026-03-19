package com.lite_k8s.approval;

import com.lite_k8s.playbook.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalServiceTest {

    private ApprovalService approvalService;
    private PendingApprovalRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PendingApprovalRepository();
        approvalService = new ApprovalService(repository);
    }

    @Test
    @DisplayName("승인 요청 생성")
    void shouldCreateApprovalRequest() {
        // when
        PendingApproval approval = approvalService.requestApproval(
                "container-restart", "web-server", RiskLevel.HIGH
        );

        // then
        assertThat(approval.getId()).isNotNull();
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(repository.findById(approval.getId())).isPresent();
    }

    @Test
    @DisplayName("승인 처리")
    void shouldApproveRequest() {
        // given
        PendingApproval approval = approvalService.requestApproval(
                "restart", "web", RiskLevel.HIGH
        );

        // when
        ApprovalResult result = approvalService.approve(approval.getId(), "admin");

        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getApproval().getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(result.getApproval().getApprovedBy()).isEqualTo("admin");
    }

    @Test
    @DisplayName("거부 처리")
    void shouldRejectRequest() {
        // given
        PendingApproval approval = approvalService.requestApproval(
                "restart", "web", RiskLevel.HIGH
        );

        // when
        ApprovalResult result = approvalService.reject(approval.getId(), "admin");

        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getApproval().getStatus()).isEqualTo(ApprovalStatus.REJECTED);
    }

    @Test
    @DisplayName("존재하지 않는 요청 승인 시 실패")
    void shouldFailToApproveNonExistent() {
        // when
        ApprovalResult result = approvalService.approve("non-existent", "admin");

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("not found");
    }

    @Test
    @DisplayName("이미 처리된 요청은 다시 처리할 수 없음")
    void shouldNotProcessAlreadyResolved() {
        // given
        PendingApproval approval = approvalService.requestApproval(
                "restart", "web", RiskLevel.HIGH
        );
        approvalService.approve(approval.getId(), "admin");

        // when
        ApprovalResult result = approvalService.reject(approval.getId(), "other");

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("already");
    }

    @Test
    @DisplayName("대기 중인 요청 목록 조회")
    void shouldGetPendingRequests() {
        // given
        approvalService.requestApproval("a", "c1", RiskLevel.HIGH);
        approvalService.requestApproval("b", "c2", RiskLevel.CRITICAL);
        PendingApproval approved = approvalService.requestApproval("c", "c3", RiskLevel.LOW);
        approvalService.approve(approved.getId(), "admin");

        // when
        List<PendingApproval> pending = approvalService.getPendingRequests();

        // then
        assertThat(pending).hasSize(2);
    }

    @Test
    @DisplayName("만료된 요청 처리")
    void shouldProcessExpiredRequests() {
        // given
        PendingApproval approval = approvalService.requestApproval(
                "restart", "web", RiskLevel.HIGH
        );
        approval.setExpiresAt(approval.getRequestedAt().minusMinutes(1)); // 강제 만료

        // when
        int processed = approvalService.processExpiredRequests();

        // then
        assertThat(processed).isEqualTo(1);
        assertThat(repository.findById(approval.getId()).get().getStatus())
                .isEqualTo(ApprovalStatus.EXPIRED);
    }

    @Test
    @DisplayName("ID로 승인 요청 조회")
    void shouldFindById() {
        // given
        PendingApproval approval = approvalService.requestApproval(
                "restart", "web", RiskLevel.HIGH
        );

        // when
        Optional<PendingApproval> found = approvalService.findById(approval.getId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getPlaybookName()).isEqualTo("restart");
    }
}
