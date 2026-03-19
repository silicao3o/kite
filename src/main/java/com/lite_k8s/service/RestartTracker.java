package com.lite_k8s.service;

import com.lite_k8s.config.SelfHealingProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RestartTracker {

    private final Clock clock;
    private final SelfHealingProperties properties;
    private final Map<String, RestartRecord> restartRecords = new ConcurrentHashMap<>();

    public RestartTracker(Clock clock, SelfHealingProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public void recordRestart(String containerId) {
        Instant now = clock.instant();
        restartRecords.compute(containerId, (id, record) -> {
            if (record == null) {
                return new RestartRecord(1, now);
            }
            if (isWindowExpired(record.firstRestartTime, now)) {
                return new RestartRecord(1, now);
            }
            return new RestartRecord(record.count + 1, record.firstRestartTime);
        });
    }

    public int getRestartCount(String containerId) {
        RestartRecord record = restartRecords.get(containerId);
        if (record == null) {
            return 0;
        }
        if (isWindowExpired(record.firstRestartTime, clock.instant())) {
            return 0;
        }
        return record.count;
    }

    public boolean isMaxRestartsExceeded(String containerId, int maxRestarts) {
        return getRestartCount(containerId) >= maxRestarts;
    }

    private boolean isWindowExpired(Instant firstRestartTime, Instant now) {
        long windowSeconds = properties.getResetWindowMinutes() * 60L;
        return now.isAfter(firstRestartTime.plusSeconds(windowSeconds));
    }

    private record RestartRecord(int count, Instant firstRestartTime) {}
}
