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

    @Mock
    private DockerService dockerService;

    private MonitorProperties monitorProperties;
    private RestartLoopAlertService restartLoopAlertService;

    @BeforeEach
    void setUp() {
        monitorProperties = new MonitorProperties();
        monitorProperties.getRestartLoop().setEnabled(true);
        monitorProperties.getRestartLoop().setThresholdCount(3);
        monitorProperties.getRestartLoop().setWindowMinutes(5);
        restartLoopAlertService = new RestartLoopAlertService(
                emailNotificationService, deduplicationService, restartTracker, monitorProperties, dockerService);
    }

    @Test
    @DisplayName("5분 내 3회 재시작하면 컨테이너를 정지하고 crash loop 알림을 발송한다")
    void shouldStopContainerAndAlertWhenCrashLoopDetected() {
        // given
        String containerId = "c1";
        String containerName = "web-server";
        String nodeId = "node-1";
        when(restartTracker.getRestartCount(containerId)).thenReturn(3);
        when(deduplicationService.shouldAlert(containerId, "CRASH_LOOP")).thenReturn(true);

        // when
        restartLoopAlertService.checkAndHandle(containerId, containerName, nodeId);

        // then
        verify(dockerService).stopContainer(containerId, nodeId);
        verify(emailNotificationService).sendCrashLoopStoppedAlert(
                eq(containerName), eq(containerId), eq(3), eq(5));
    }

    @Test
    @DisplayName("재시작 횟수가 임계치 미만이면 정지하지 않는다")
    void shouldNotStopWhenBelowThreshold() {
        // given
        String containerId = "c1";
        String containerName = "web-server";
        when(restartTracker.getRestartCount(containerId)).thenReturn(2);

        // when
        restartLoopAlertService.checkAndHandle(containerId, containerName, null);

        // then
        verify(dockerService, never()).stopContainer(anyString(), anyString());
        verify(emailNotificationService, never()).sendCrashLoopStoppedAlert(
                anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("중복 방지 서비스가 차단하면 정지하지 않는다")
    void shouldNotStopWhenDeduplicationBlocks() {
        // given
        String containerId = "c1";
        String containerName = "web-server";
        when(restartTracker.getRestartCount(containerId)).thenReturn(3);
        when(deduplicationService.shouldAlert(containerId, "CRASH_LOOP")).thenReturn(false);

        // when
        restartLoopAlertService.checkAndHandle(containerId, containerName, null);

        // then
        verify(dockerService, never()).stopContainer(anyString(), anyString());
        verify(emailNotificationService, never()).sendCrashLoopStoppedAlert(
                anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("crash loop 감지가 비활성화되면 아무것도 하지 않는다")
    void shouldDoNothingWhenDisabled() {
        // given
        monitorProperties.getRestartLoop().setEnabled(false);
        String containerId = "c1";
        String containerName = "web-server";

        // when
        restartLoopAlertService.checkAndHandle(containerId, containerName, null);

        // then
        verify(restartTracker, never()).getRestartCount(anyString());
        verify(dockerService, never()).stopContainer(anyString(), anyString());
        verify(emailNotificationService, never()).sendCrashLoopStoppedAlert(
                anyString(), anyString(), anyInt(), anyInt());
    }
}
