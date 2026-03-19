package com.lite_k8s.approval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 승인 요청 만료 스케줄러
 *
 * 30초마다 만료된 승인 요청을 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalExpiryScheduler {

    private final ApprovalService approvalService;

    /**
     * 만료된 승인 요청 처리 (30초마다)
     */
    @Scheduled(fixedRate = 30000)
    public void processExpiredApprovals() {
        int processed = approvalService.processExpiredRequests();
        if (processed > 0) {
            log.info("Processed {} expired approval requests", processed);
        }
    }
}
