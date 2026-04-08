package com.lite_k8s.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OwnActionTrackerTest {

    @Test
    void isOwnAction_shouldReturnFalseWhenNotMarked() {
        OwnActionTracker tracker = new OwnActionTracker();

        assertThat(tracker.isOwnAction("abc123")).isFalse();
    }

    @Test
    void isOwnAction_shouldReturnTrueImmediatelyAfterMark() {
        OwnActionTracker tracker = new OwnActionTracker();

        tracker.markOwnAction("abc123");

        assertThat(tracker.isOwnAction("abc123")).isTrue();
    }

    @Test
    void isOwnAction_shouldTrackMultipleContainersIndependently() {
        OwnActionTracker tracker = new OwnActionTracker();

        tracker.markOwnAction("abc123");

        assertThat(tracker.isOwnAction("abc123")).isTrue();
        assertThat(tracker.isOwnAction("xyz789")).isFalse();
    }

    @Test
    void isOwnAction_shouldReturnFalseAfterTtlExpired() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-04-08T00:00:00Z"));
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        OwnActionTracker tracker = new OwnActionTracker(clock, Duration.ofSeconds(10));

        tracker.markOwnAction("abc123");
        assertThat(tracker.isOwnAction("abc123")).isTrue();

        // advance 11 seconds → expired
        now.set(now.get().plusSeconds(11));
        assertThat(tracker.isOwnAction("abc123")).isFalse();
    }

    @Test
    void isOwnAction_shouldReturnTrueJustBeforeTtl() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-04-08T00:00:00Z"));
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        OwnActionTracker tracker = new OwnActionTracker(clock, Duration.ofSeconds(10));

        tracker.markOwnAction("abc123");
        now.set(now.get().plusSeconds(9));

        assertThat(tracker.isOwnAction("abc123")).isTrue();
    }

    @Test
    void markOwnAction_shouldRefreshTimestampOnSecondCall() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-04-08T00:00:00Z"));
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        OwnActionTracker tracker = new OwnActionTracker(clock, Duration.ofSeconds(10));

        tracker.markOwnAction("abc123");
        now.set(now.get().plusSeconds(8));
        tracker.markOwnAction("abc123"); // refresh
        now.set(now.get().plusSeconds(8)); // 16s from first, 8s from refresh

        assertThat(tracker.isOwnAction("abc123")).isTrue();
    }
}
