package com.lite_k8s.approval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalExpirySchedulerTest {

    @Mock
    private ApprovalService approvalService;

    private ApprovalExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ApprovalExpiryScheduler(approvalService);
    }

    @Test
    @DisplayName("스케줄러가 만료된 요청을 처리한다")
    void shouldProcessExpiredRequests() {
        // given
        when(approvalService.processExpiredRequests()).thenReturn(3);

        // when
        scheduler.processExpiredApprovals();

        // then
        verify(approvalService, times(1)).processExpiredRequests();
    }

    @Test
    @DisplayName("만료된 요청이 없어도 정상 동작")
    void shouldHandleNoExpiredRequests() {
        // given
        when(approvalService.processExpiredRequests()).thenReturn(0);

        // when
        scheduler.processExpiredApprovals();

        // then
        verify(approvalService, times(1)).processExpiredRequests();
    }
}
