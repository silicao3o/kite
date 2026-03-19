package com.lite_k8s.service;

import com.lite_k8s.config.MonitorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertDeduplicationService {

    private final MonitorProperties monitorProperties;

    // 컨테이너ID + 액션 -> 마지막 알림 시간
    private final ConcurrentMap<String, Instant> recentAlerts = new ConcurrentHashMap<>();

    /**
     * 알림을 보내도 되는지 확인 (중복 체크)
     *
     * @param containerId 컨테이너 ID
     * @param action      이벤트 액션 (die, kill, oom)
     * @return true면 알림 전송, false면 중복으로 스킵
     */
    public boolean shouldAlert(String containerId, String action) {
        MonitorProperties.Deduplication config = monitorProperties.getDeduplication();

        if (!config.isEnabled()) {
            return true;
        }

        String key = generateKey(containerId, action);
        Instant now = Instant.now();
        Instant lastAlert = recentAlerts.get(key);

        if (lastAlert != null) {
            long secondsSinceLastAlert = now.getEpochSecond() - lastAlert.getEpochSecond();
            if (secondsSinceLastAlert < config.getWindowSeconds()) {
                log.debug("중복 알림 스킵: {} ({}초 전 알림 발송됨)", key, secondsSinceLastAlert);
                return false;
            }
        }

        // 알림 시간 기록
        recentAlerts.put(key, now);
        return true;
    }

    /**
     * 중복 체크 키 생성
     */
    private String generateKey(String containerId, String action) {
        return containerId + ":" + action;
    }

    /**
     * 만료된 알림 기록 정리 (1분마다 실행)
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredAlerts() {
        int windowSeconds = monitorProperties.getDeduplication().getWindowSeconds();
        Instant cutoff = Instant.now().minusSeconds(windowSeconds * 2L);

        int removed = 0;
        var iterator = recentAlerts.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isBefore(cutoff)) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            log.debug("만료된 알림 기록 {}개 정리됨", removed);
        }
    }

    /**
     * 테스트용: 알림 기록 초기화
     */
    public void clear() {
        recentAlerts.clear();
    }
}
