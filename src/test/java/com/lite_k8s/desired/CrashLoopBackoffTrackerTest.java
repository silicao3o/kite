package com.lite_k8s.desired;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrashLoopBackoffTrackerTest {

    private CrashLoopBackoffTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new CrashLoopBackoffTracker();
    }

    @Test
    @DisplayName("첫 재시작 대기시간은 기본값 10초")
    void getRestartDelaySeconds_FirstRestart_Returns10() {
        assertThat(tracker.getRestartDelaySeconds("demo-api")).isEqualTo(10);
    }

    @Test
    @DisplayName("재시작 기록할수록 대기시간 2배씩 증가")
    void getRestartDelaySeconds_AfterMultipleRestarts_Doubles() {
        tracker.recordRestart("demo-api"); // 1회
        assertThat(tracker.getRestartDelaySeconds("demo-api")).isEqualTo(20);

        tracker.recordRestart("demo-api"); // 2회
        assertThat(tracker.getRestartDelaySeconds("demo-api")).isEqualTo(40);

        tracker.recordRestart("demo-api"); // 3회
        assertThat(tracker.getRestartDelaySeconds("demo-api")).isEqualTo(80);
    }

    @Test
    @DisplayName("최대 대기시간은 300초로 제한")
    void getRestartDelaySeconds_AfterManyRestarts_CapsAt300() {
        for (int i = 0; i < 10; i++) {
            tracker.recordRestart("demo-api");
        }
        assertThat(tracker.getRestartDelaySeconds("demo-api")).isEqualTo(300);
    }

    @Test
    @DisplayName("리셋 후 대기시간 초기화")
    void getRestartDelaySeconds_AfterReset_Returns10() {
        tracker.recordRestart("demo-api");
        tracker.recordRestart("demo-api");
        tracker.reset("demo-api");

        assertThat(tracker.getRestartDelaySeconds("demo-api")).isEqualTo(10);
    }

    @Test
    @DisplayName("다른 서비스는 독립적으로 추적")
    void getRestartDelaySeconds_DifferentServices_Independent() {
        tracker.recordRestart("service-a");
        tracker.recordRestart("service-a");

        assertThat(tracker.getRestartDelaySeconds("service-a")).isEqualTo(40);
        assertThat(tracker.getRestartDelaySeconds("service-b")).isEqualTo(10);
    }

    @Test
    @DisplayName("재시작 횟수 조회")
    void getRestartCount_ReturnsCorrectCount() {
        tracker.recordRestart("demo-api");
        tracker.recordRestart("demo-api");

        assertThat(tracker.getRestartCount("demo-api")).isEqualTo(2);
    }
}
