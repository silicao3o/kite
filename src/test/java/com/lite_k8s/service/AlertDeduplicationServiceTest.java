package com.lite_k8s.service;

import com.lite_k8s.config.MonitorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertDeduplicationServiceTest {

    private MonitorProperties monitorProperties;
    private AlertDeduplicationService deduplicationService;

    @BeforeEach
    void setUp() {
        monitorProperties = new MonitorProperties();
        deduplicationService = new AlertDeduplicationService(monitorProperties);
    }

    @Test
    @DisplayName("첫 번째 알림은 허용")
    void shouldAlert_FirstAlert_ShouldReturnTrue() {
        // when
        boolean result = deduplicationService.shouldAlert("container123", "die");

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("같은 컨테이너 동일 액션 중복 알림은 차단")
    void shouldAlert_DuplicateAlert_ShouldReturnFalse() {
        // given
        deduplicationService.shouldAlert("container123", "die");

        // when
        boolean result = deduplicationService.shouldAlert("container123", "die");

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("같은 컨테이너 다른 액션은 허용")
    void shouldAlert_SameContainerDifferentAction_ShouldReturnTrue() {
        // given
        deduplicationService.shouldAlert("container123", "die");

        // when
        boolean result = deduplicationService.shouldAlert("container123", "kill");

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("다른 컨테이너는 허용")
    void shouldAlert_DifferentContainer_ShouldReturnTrue() {
        // given
        deduplicationService.shouldAlert("container123", "die");

        // when
        boolean result = deduplicationService.shouldAlert("container456", "die");

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("중복 방지 비활성화 시 항상 허용")
    void shouldAlert_WhenDisabled_ShouldAlwaysReturnTrue() {
        // given
        monitorProperties.getDeduplication().setEnabled(false);
        deduplicationService.shouldAlert("container123", "die");

        // when
        boolean result = deduplicationService.shouldAlert("container123", "die");

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("시간 창 경과 후 다시 허용")
    void shouldAlert_AfterWindowExpires_ShouldReturnTrue() throws InterruptedException {
        // given
        monitorProperties.getDeduplication().setWindowSeconds(1); // 1초로 설정
        deduplicationService.shouldAlert("container123", "die");

        // when
        Thread.sleep(1100); // 1.1초 대기
        boolean result = deduplicationService.shouldAlert("container123", "die");

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("clear 호출 시 기록 초기화")
    void clear_ShouldResetAllRecords() {
        // given
        deduplicationService.shouldAlert("container123", "die");
        deduplicationService.shouldAlert("container456", "die");

        // when
        deduplicationService.clear();

        // then
        assertThat(deduplicationService.shouldAlert("container123", "die")).isTrue();
        assertThat(deduplicationService.shouldAlert("container456", "die")).isTrue();
    }
}
