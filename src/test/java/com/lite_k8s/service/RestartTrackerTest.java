package com.lite_k8s.service;

import com.lite_k8s.config.SelfHealingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestartTrackerTest {

    private RestartTracker tracker;
    private Clock clock;
    private SelfHealingProperties properties;

    @BeforeEach
    void setUp() {
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.now());
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());

        properties = new SelfHealingProperties();
        properties.setResetWindowMinutes(30); // 기본 30분

        tracker = new RestartTracker(clock, properties);
    }

    @Test
    void shouldRecordRestartCount() {
        tracker.recordRestart("container-1");

        assertThat(tracker.getRestartCount("container-1")).isEqualTo(1);
    }

    @Test
    void shouldIncrementRestartCount() {
        tracker.recordRestart("container-1");
        tracker.recordRestart("container-1");
        tracker.recordRestart("container-1");

        assertThat(tracker.getRestartCount("container-1")).isEqualTo(3);
    }

    @Test
    void shouldReturnZeroForUnknownContainer() {
        assertThat(tracker.getRestartCount("unknown")).isEqualTo(0);
    }

    @Test
    void shouldCheckIfMaxRestartsExceeded() {
        tracker.recordRestart("container-1");
        tracker.recordRestart("container-1");
        tracker.recordRestart("container-1");

        assertThat(tracker.isMaxRestartsExceeded("container-1", 3)).isTrue();
        assertThat(tracker.isMaxRestartsExceeded("container-1", 5)).isFalse();
    }

    @Test
    void shouldResetRestartCountAfterTimeWindow() {
        Instant now = Instant.now();
        when(clock.instant()).thenReturn(now);

        tracker.recordRestart("container-1");
        tracker.recordRestart("container-1");
        assertThat(tracker.getRestartCount("container-1")).isEqualTo(2);

        // 31분 후 (30분 윈도우 초과)
        when(clock.instant()).thenReturn(now.plusSeconds(31 * 60));
        tracker.recordRestart("container-1");

        // 카운트가 리셋되고 새로 1부터 시작
        assertThat(tracker.getRestartCount("container-1")).isEqualTo(1);
    }

    @Test
    void shouldNotResetRestartCountWithinTimeWindow() {
        Instant now = Instant.now();
        when(clock.instant()).thenReturn(now);

        tracker.recordRestart("container-1");
        tracker.recordRestart("container-1");

        // 29분 후 (30분 윈도우 이내)
        when(clock.instant()).thenReturn(now.plusSeconds(29 * 60));
        tracker.recordRestart("container-1");

        // 카운트가 유지됨
        assertThat(tracker.getRestartCount("container-1")).isEqualTo(3);
    }
}
