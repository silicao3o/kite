package com.lite_k8s.approval;

import com.lite_k8s.playbook.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalControllerTest {

    @Mock
    private ApprovalService approvalService;

    private ApprovalController controller;

    @BeforeEach
    void setUp() {
        controller = new ApprovalController(approvalService);
    }

    @Test
    @DisplayName("대기 중인 승인 목록 조회")
    void shouldGetPendingApprovals() {
        // given
        PendingApproval approval1 = PendingApproval.create("restart", "web", RiskLevel.HIGH);
        PendingApproval approval2 = PendingApproval.create("kill", "db", RiskLevel.CRITICAL);
        when(approvalService.getPendingRequests()).thenReturn(List.of(approval1, approval2));

        // when
        List<PendingApproval> result = controller.getPendingApprovals();

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("승인 처리 API")
    void shouldApprove() {
        // given
        String id = "test-id";
        PendingApproval approval = PendingApproval.create("restart", "web", RiskLevel.HIGH);
        when(approvalService.approve(id, "admin")).thenReturn(ApprovalResult.success(approval));

        // when
        ApprovalResult result = controller.approve(id, "admin");

        // then
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("거부 처리 API")
    void shouldReject() {
        // given
        String id = "test-id";
        PendingApproval approval = PendingApproval.create("restart", "web", RiskLevel.HIGH);
        approval.reject("admin");
        when(approvalService.reject(id, "admin")).thenReturn(ApprovalResult.success(approval));

        // when
        ApprovalResult result = controller.reject(id, "admin");

        // then
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("대기 중인 승인 개수 조회")
    void shouldGetPendingCount() {
        // given
        when(approvalService.getPendingRequests()).thenReturn(List.of(
                PendingApproval.create("a", "c1", RiskLevel.HIGH),
                PendingApproval.create("b", "c2", RiskLevel.CRITICAL)
        ));

        // when
        int count = controller.getPendingCount();

        // then
        assertThat(count).isEqualTo(2);
    }
}
