package com.lite_k8s.update;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListContainersCmd;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageUpdatePollerIntervalTest {

    @Mock private GhcrClient ghcrClient;
    @Mock private DockerClient dockerClient;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ListContainersCmd listContainersCmd;
    @Mock private ImageWatchService watchService;
    @Mock private ImageUpdateHistoryService historyService;

    private ImageWatchProperties properties;
    private ImageUpdatePoller poller;

    @BeforeEach
    void setUp() {
        properties = new ImageWatchProperties();
        properties.setEnabled(true);
        properties.setPollIntervalSeconds(300);
        poller = new ImageUpdatePoller(properties, watchService, ghcrClient, dockerClient,
                eventPublisher, historyService);
    }

    @Test
    @DisplayName("pollIntervalSeconds가 null이면 항상 폴링한다 (글로벌 주기 사용)")
    void isDueForPolling_WithNullInterval_AlwaysTrue() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .build(); // pollIntervalSeconds = null

        assertThat(poller.isDueForPolling(watch)).isTrue();
    }

    @Test
    @DisplayName("pollIntervalSeconds가 설정되고 처음 폴링이면 true")
    void isDueForPolling_WithInterval_FirstTime_ReturnsTrue() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .pollIntervalSeconds(60)
                .build();

        assertThat(poller.isDueForPolling(watch)).isTrue();
    }

    @Test
    @DisplayName("pollIntervalSeconds가 설정되고 주기 미도래 시 false")
    void pollAll_WithWatchInterval_SkipsIfNotDue() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .pollIntervalSeconds(600) // 10분
                .build();

        when(watchService.findEnabled()).thenReturn(List.of(watch));
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any())).thenReturn(null);

        // 첫 번째 호출 — 폴링 실행
        poller.pollAll();
        verify(ghcrClient, times(1)).getLatestDigest(anyString(), anyString(), any());

        // 두 번째 호출 — 주기 미도래로 스킵
        poller.pollAll();
        verify(ghcrClient, times(1)).getLatestDigest(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("getEffectiveInterval: pollIntervalSeconds가 설정되면 해당 값 반환")
    void getEffectiveInterval_WithWatchInterval() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .pollIntervalSeconds(60)
                .build();

        assertThat(poller.getEffectiveInterval(watch)).isEqualTo(60);
    }

    @Test
    @DisplayName("getEffectiveInterval: pollIntervalSeconds가 null이면 글로벌 기본값 반환")
    void getEffectiveInterval_WithNullInterval_ReturnsGlobal() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .build(); // pollIntervalSeconds = null

        assertThat(poller.getEffectiveInterval(watch)).isEqualTo(300);
    }
}
