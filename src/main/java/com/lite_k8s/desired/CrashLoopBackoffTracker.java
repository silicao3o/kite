package com.lite_k8s.desired;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CrashLoopBackOff 재시작 간격 추적
 *
 * K8s와 유사한 지수 백오프:
 * 10s → 20s → 40s → 80s → 160s → 300s (cap)
 */
@Slf4j
@Component
public class CrashLoopBackoffTracker {

    private static final int BASE_DELAY_SECONDS = 10;
    private static final int MAX_DELAY_SECONDS = 300;

    private final ConcurrentHashMap<String, AtomicInteger> restartCounts = new ConcurrentHashMap<>();

    /**
     * 현재 재시작 대기 시간 반환 (초)
     * count=0: 10s, count=1: 20s, count=2: 40s, ...
     */
    public int getRestartDelaySeconds(String serviceKey) {
        int count = getRestartCount(serviceKey);
        int delay = BASE_DELAY_SECONDS * (int) Math.pow(2, count);
        return Math.min(delay, MAX_DELAY_SECONDS);
    }

    /** 재시작 기록 (count 증가) */
    public void recordRestart(String serviceKey) {
        restartCounts.computeIfAbsent(serviceKey, k -> new AtomicInteger(0))
                .incrementAndGet();
        log.info("CrashLoopBackOff [{}]: 재시작 {}회 → 다음 대기 {}초",
                serviceKey, getRestartCount(serviceKey), getRestartDelaySeconds(serviceKey));
    }

    /** 정상 복구 시 리셋 */
    public void reset(String serviceKey) {
        restartCounts.remove(serviceKey);
        log.info("CrashLoopBackOff [{}]: 리셋", serviceKey);
    }

    public int getRestartCount(String serviceKey) {
        AtomicInteger counter = restartCounts.get(serviceKey);
        return counter != null ? counter.get() : 0;
    }
}
