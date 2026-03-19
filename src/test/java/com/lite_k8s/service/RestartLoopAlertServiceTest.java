package com.lite_k8s.service;

import com.lite_k8s.config.MonitorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestartLoopAlertServiceTest {

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private AlertDeduplicationService deduplicationService;

    @Mock
    private RestartTracker restartTracker;

    private MonitorProperties monitorProperties;
    private RestartLoopAlertService restartLoopAlertService;

    @BeforeEach
    void setUp() {
        monitorProperties = new MonitorProperties();
        monitorProperties.getRestartLoop().setEnabled(true);
        monitorProperties.getRestartLoop().setThresholdCount(3);
        monitorProperties.getRestartLoop().setWindowMinutes(5);
        restartLoopAlertService = new RestartLoopAlertService(
                emailNotificationService, deduplicationService, restartTracker, monitorProperties);
    }

    @Test
    @DisplayName("5분 내 3회 재시작하면 알림을 발송한다")
    void shouldAlertWhenRestartLoopDetected() {
        // given
        String containerId = "c1";
        String containerName = "web-server";
        when(restartTracker.getRestartCount(containerId)).thenReturn(3);
        when(deduplicationService.shouldAlert(containerId, "RESTART_LOOP")).thenReturn(true);

        // when
        restartLoopAlertService.checkAndAlert(containerId, containerName);

        // then
        verify(emailNotificationService).sendRestartLoopAlert(
                eq(containerName), eq(containerId), eq(3), eq(5));
    }

    @Test
    @DisplayName("재시작 횟수가 임계치 미만이면 알림을 발송하지 않는다")
    void shouldNotAlertWhenBelowThreshold() {
        // given
        String containerId = "c1";
        String containerName = "web-server";
        when(restartTracker.getRestartCount(containerId)).thenReturn(2);

        // when
        restartLoopAlertService.checkAndAlert(containerId, containerName);

        // then
        verify(emailNotificationService, never()).sendRestartLoopAlert(
                anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("중복 방지 서비스가 차단하면 알림을 발송하지 않는다")
    void shouldNotAlertWhenDeduplicationBlocks() {
        // given
        String containerId = "c1";
        String containerName = "web-server";
        when(restartTracker.getRestartCount(containerId)).thenReturn(3);
        when(deduplicationService.shouldAlert(containerId, "RESTART_LOOP")).thenReturn(false);

        // when
        restartLoopAlertService.checkAndAlert(containerId, containerName);

        // then
        verify(emailNotificationService, never()).sendRestartLoopAlert(
                anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("재시작 반복 알림이 비활성화되면 알림을 발송하지 않는다")
    void shouldNotAlertWhenDisabled() {
        // given
        monitorProperties.getRestartLoop().setEnabled(false);
        String containerId = "c1";
        String containerName = "web-server";

        // when
        restartLoopAlertService.checkAndAlert(containerId, containerName);

        // then
        verify(restartTracker, never()).getRestartCount(anyString());
        verify(emailNotificationService, never()).sendRestartLoopAlert(
                anyString(), anyString(), anyInt(), anyInt());
    }
}
