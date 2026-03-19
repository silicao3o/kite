package com.lite_k8s.service;

import com.lite_k8s.config.MonitorProperties;
import com.lite_k8s.model.ContainerMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThresholdAlertServiceTest {

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private AlertDeduplicationService deduplicationService;

    private MonitorProperties monitorProperties;
    private ThresholdAlertService thresholdAlertService;

    @BeforeEach
    void setUp() {
        monitorProperties = new MonitorProperties();
        monitorProperties.getMetrics().setCpuThresholdPercent(80.0);
        monitorProperties.getMetrics().setMemoryThresholdPercent(90.0);
        thresholdAlertService = new ThresholdAlertService(
                emailNotificationService, deduplicationService, monitorProperties);
    }

    @Test
    @DisplayName("CPU 사용률이 임계치를 초과하면 알림을 발송한다")
    void shouldAlertWhenCpuExceedsThreshold() {
        // given
        ContainerMetrics metrics = createMetrics("c1", "web-server", 85.0, 50.0);
        when(deduplicationService.shouldAlert("c1", "CPU_THRESHOLD")).thenReturn(true);

        // when
        thresholdAlertService.checkAndAlert(metrics);

        // then
        verify(emailNotificationService).sendCpuThresholdAlert(
                eq("web-server"), eq("c1"), eq(85.0), eq(80.0));
    }

    @Test
    @DisplayName("메모리 사용률이 임계치를 초과하면 알림을 발송한다")
    void shouldAlertWhenMemoryExceedsThreshold() {
        // given
        ContainerMetrics metrics = createMetrics("c1", "web-server", 50.0, 95.0);
        when(deduplicationService.shouldAlert("c1", "MEMORY_THRESHOLD")).thenReturn(true);

        // when
        thresholdAlertService.checkAndAlert(metrics);

        // then
        verify(emailNotificationService).sendMemoryThresholdAlert(
                eq("web-server"), eq("c1"), eq(95.0), eq(90.0));
    }

    @Test
    @DisplayName("CPU와 메모리 모두 임계치를 초과하면 두 알림 모두 발송한다")
    void shouldAlertBothWhenBothExceedThreshold() {
        // given
        ContainerMetrics metrics = createMetrics("c1", "web-server", 85.0, 95.0);
        when(deduplicationService.shouldAlert("c1", "CPU_THRESHOLD")).thenReturn(true);
        when(deduplicationService.shouldAlert("c1", "MEMORY_THRESHOLD")).thenReturn(true);

        // when
        thresholdAlertService.checkAndAlert(metrics);

        // then
        verify(emailNotificationService).sendCpuThresholdAlert(anyString(), anyString(), anyDouble(), anyDouble());
        verify(emailNotificationService).sendMemoryThresholdAlert(anyString(), anyString(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("임계치 미만이면 알림을 발송하지 않는다")
    void shouldNotAlertWhenBelowThreshold() {
        // given
        ContainerMetrics metrics = createMetrics("c1", "web-server", 50.0, 60.0);

        // when
        thresholdAlertService.checkAndAlert(metrics);

        // then
        verify(emailNotificationService, never()).sendCpuThresholdAlert(anyString(), anyString(), anyDouble(), anyDouble());
        verify(emailNotificationService, never()).sendMemoryThresholdAlert(anyString(), anyString(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("중복 방지 서비스가 차단하면 알림을 발송하지 않는다")
    void shouldNotAlertWhenDeduplicationBlocks() {
        // given
        ContainerMetrics metrics = createMetrics("c1", "web-server", 85.0, 95.0);
        when(deduplicationService.shouldAlert("c1", "CPU_THRESHOLD")).thenReturn(false);
        when(deduplicationService.shouldAlert("c1", "MEMORY_THRESHOLD")).thenReturn(false);

        // when
        thresholdAlertService.checkAndAlert(metrics);

        // then
        verify(emailNotificationService, never()).sendCpuThresholdAlert(anyString(), anyString(), anyDouble(), anyDouble());
        verify(emailNotificationService, never()).sendMemoryThresholdAlert(anyString(), anyString(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("임계치 알림이 비활성화되면 알림을 발송하지 않는다")
    void shouldNotAlertWhenDisabled() {
        // given
        monitorProperties.getMetrics().setThresholdAlertEnabled(false);
        ContainerMetrics metrics = createMetrics("c1", "web-server", 85.0, 95.0);

        // when
        thresholdAlertService.checkAndAlert(metrics);

        // then
        verify(emailNotificationService, never()).sendCpuThresholdAlert(anyString(), anyString(), anyDouble(), anyDouble());
        verify(emailNotificationService, never()).sendMemoryThresholdAlert(anyString(), anyString(), anyDouble(), anyDouble());
    }

    private ContainerMetrics createMetrics(String id, String name, double cpuPercent, double memoryPercent) {
        return ContainerMetrics.builder()
                .containerId(id)
                .containerName(name)
                .cpuPercent(cpuPercent)
                .memoryPercent(memoryPercent)
                .memoryUsage((long) (memoryPercent * 10 * 1024 * 1024))
                .memoryLimit(1024 * 1024 * 1024L)
                .collectedAt(LocalDateTime.now())
                .build();
    }
}
