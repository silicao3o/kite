package com.lite_k8s.health;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 컨테이너별 probe 연속 실패/성공 횟수 추적
 * key: containerId + ":" + probeType (liveness / readiness)
 */
@Component
public class HealthCheckStateTracker {

    private final ConcurrentHashMap<String, AtomicInteger> failureCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> containerStartTime = new ConcurrentHashMap<>();

    public int recordFailure(String containerId, String probeType) {
        String key = key(containerId, probeType);
        return failureCount.computeIfAbsent(key, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    public void recordSuccess(String containerId, String probeType) {
        failureCount.remove(key(containerId, probeType));
    }

    public void reset(String containerId, String probeType) {
        failureCount.remove(key(containerId, probeType));
    }

    public int getFailureCount(String containerId, String probeType) {
        AtomicInteger counter = failureCount.get(key(containerId, probeType));
        return counter != null ? counter.get() : 0;
    }

    /** 컨테이너 시작 시각 기록 (initialDelaySeconds 계산용) */
    public void recordContainerStart(String containerId) {
        containerStartTime.put(containerId, System.currentTimeMillis());
    }

    /** initialDelay가 경과했는지 확인 */
    public boolean isInitialDelayElapsed(String containerId, int initialDelaySeconds) {
        Long startTime = containerStartTime.get(containerId);
        if (startTime == null) return true; // 기록 없으면 경과로 간주
        return System.currentTimeMillis() - startTime >= (long) initialDelaySeconds * 1000;
    }

    private String key(String containerId, String probeType) {
        return containerId + ":" + probeType;
    }
}
