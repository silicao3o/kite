package com.lite_k8s.approval;

import com.lite_k8s.playbook.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PendingApprovalTest {

    @Test
    @DisplayName("PendingApproval 생성 시 기본값 설정")
    void shouldSetDefaultValuesOnCreation() {
        // given
        String playbookName = "container-restart";
        String containerName = "web-server";
        RiskLevel riskLevel = RiskLevel.HIGH;

        // when
        PendingApproval approval = PendingApproval.create(
                playbookName, containerName, riskLevel
        );

        // then
        assertThat(approval.getId()).isNotNull();
        assertThat(approval.getPlaybookName()).isEqualTo(playbookName);
        assertThat(approval.getContainerName()).isEqualTo(containerName);
        assertThat(approval.getRiskLevel()).isEqualTo(riskLevel);
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(approval.getRequestedAt()).isNotNull();
        assertThat(approval.getExpiresAt()).isAfter(approval.getRequestedAt());
    }

    @Test
    @DisplayName("만료 시간은 요청 시간 + 5분")
    void shouldExpireAfter5Minutes() {
        // given & when
        PendingApproval approval = PendingApproval.create(
                "test-playbook", "test-container", RiskLevel.CRITICAL
        );

        // then
        LocalDateTime expectedExpiry = approval.getRequestedAt().plusMinutes(5);
        assertThat(approval.getExpiresAt()).isEqualTo(expectedExpiry);
    }

    @Test
    @DisplayName("승인 처리")
    void shouldApprove() {
        // given
        PendingApproval approval = PendingApproval.create(
                "restart", "web", RiskLevel.HIGH
        );

        // when
        approval.approve("admin");

        // then
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approval.getApprovedBy()).isEqualTo("admin");
        assertThat(approval.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("거부 처리")
    void shouldReject() {
        // given
        PendingApproval approval = PendingApproval.create(
                "restart", "web", RiskLevel.HIGH
        );

        // when
        approval.reject("admin");

        // then
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(approval.getApprovedBy()).isEqualTo("admin");
        assertThat(approval.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("만료 여부 확인 - 만료됨")
    void shouldDetectExpired() {
        // given
        PendingApproval approval = PendingApproval.create(
                "restart", "web", RiskLevel.HIGH
        );
        // 만료 시간을 과거로 설정
        approval.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        // when & then
        assertThat(approval.isExpired()).isTrue();
    }

    @Test
    @DisplayName("만료 여부 확인 - 유효함")
    void shouldDetectNotExpired() {
        // given
        PendingApproval approval = PendingApproval.create(
                "restart", "web", RiskLevel.HIGH
        );

        // when & then
        assertThat(approval.isExpired()).isFalse();
    }

    @Test
    @DisplayName("만료 처리")
    void shouldExpire() {
        // given
        PendingApproval approval = PendingApproval.create(
                "restart", "web", RiskLevel.HIGH
        );

        // when
        approval.expire();

        // then
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.EXPIRED);
        assertThat(approval.getResolvedAt()).isNotNull();
    }
}
