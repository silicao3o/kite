package com.lite_k8s.update;

import com.github.dockerjava.api.DockerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class ImageUpdatePollerIntervalTest {

    @Mock private GhcrClient ghcrClient;
    @Mock private DockerClient dockerClient;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ImageWatchService watchService;
    @Mock private ImageUpdateHistoryService historyService;
    @Mock private TaskScheduler taskScheduler;
    @Mock private ScheduledFuture<?> scheduledFuture;
    @Mock private ImageMatchPolicy imageMatchPolicy;

    private ImageWatchProperties properties;
    private ImageUpdatePoller poller;

    @BeforeEach
    void setUp() {
        properties = new ImageWatchProperties();
        properties.setEnabled(true);
        poller = new ImageUpdatePoller(properties, watchService, ghcrClient, dockerClient,
                eventPublisher, historyService, imageMatchPolicy);
        poller.setTaskScheduler(taskScheduler);
    }

    @Test
    @DisplayName("scheduleWatch는 와치의 pollIntervalSeconds로 스케줄을 등록한다")
    void scheduleWatch_RegistersWithCorrectInterval() {
        doReturn(scheduledFuture).when(taskScheduler)
                .scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));

        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .pollIntervalSeconds(60)
                .build();

        poller.scheduleWatch(watch);

        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("scheduleWatch는 기본 pollIntervalSeconds(300초)로 스케줄을 등록한다")
    void scheduleWatch_DefaultInterval() {
        doReturn(scheduledFuture).when(taskScheduler)
                .scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));

        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .build(); // pollIntervalSeconds = 300

        poller.scheduleWatch(watch);

        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(300)));
    }

    @Test
    @DisplayName("scheduleWatch는 최소 10초를 보장한다")
    void scheduleWatch_MinimumInterval() {
        doReturn(scheduledFuture).when(taskScheduler)
                .scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));

        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .pollIntervalSeconds(3) // 10초 미만
                .build();

        poller.scheduleWatch(watch);

        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(10)));
    }

    @Test
    @DisplayName("scheduleWatch는 기존 스케줄을 취소하고 새로 등록한다")
    void scheduleWatch_CancelsExistingSchedule() {
        doReturn(scheduledFuture).when(taskScheduler)
                .scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));

        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .pollIntervalSeconds(60)
                .build();

        // 첫 번째 등록
        poller.scheduleWatch(watch);
        // 두 번째 등록 — 기존 것을 취소해야 한다
        poller.scheduleWatch(watch);

        verify(scheduledFuture).cancel(false);
        verify(taskScheduler, times(2)).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
    }

    @Test
    @DisplayName("비활성화된 와치는 스케줄하지 않는다")
    void scheduleWatch_DisabledWatch_NotScheduled() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .enabled(false)
                .build();

        poller.scheduleWatch(watch);

        verifyNoInteractions(taskScheduler);
    }

    @Test
    @DisplayName("scheduleAllWatches는 모든 활성 와치를 스케줄한다")
    void scheduleAllWatches_SchedulesAllEnabled() {
        doReturn(scheduledFuture).when(taskScheduler)
                .scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));

        ImageWatchEntity watch1 = ImageWatchEntity.builder().image("ghcr.io/org/app1").pollIntervalSeconds(60).build();
        ImageWatchEntity watch2 = ImageWatchEntity.builder().image("ghcr.io/org/app2").pollIntervalSeconds(120).build();
        when(watchService.findEnabled()).thenReturn(List.of(watch1, watch2));

        poller.scheduleAllWatches();

        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(60)));
        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(120)));
    }

    @Test
    @DisplayName("cancelSchedule은 특정 와치의 스케줄을 취소한다")
    void cancelSchedule_CancelsSpecificWatch() {
        doReturn(scheduledFuture).when(taskScheduler)
                .scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));

        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .pollIntervalSeconds(60)
                .build();

        poller.scheduleWatch(watch);
        poller.cancelSchedule(watch.getId());

        verify(scheduledFuture).cancel(false);
    }
}
