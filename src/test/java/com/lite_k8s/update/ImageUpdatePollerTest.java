package com.lite_k8s.update;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.command.ListContainersCmd;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageUpdatePollerTest {

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
        poller = new ImageUpdatePoller(properties, watchService, ghcrClient, dockerClient,
                eventPublisher, historyService);
    }

    @Test
    @DisplayName("비활성화 시 폴링 스킵")
    void pollAll_WhenDisabled_DoesNothing() {
        properties.setEnabled(false);

        poller.pollAll();

        verifyNoInteractions(ghcrClient, dockerClient);
    }

    @Test
    @DisplayName("15. ImageUpdatePoller가 DB에서 와치 목록을 가져온다")
    void pollAll_UsesWatchServiceInsteadOfProperties() {
        properties.setEnabled(true);

        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .tag("latest")
                .containerPattern("myapp-.*")
                .build();
        when(watchService.findEnabled()).thenReturn(List.of(watch));
        when(ghcrClient.getLatestDigest(anyString(), anyString())).thenReturn(null);

        poller.pollAll();

        verify(watchService).findEnabled();
    }

    @Test
    @DisplayName("digest 변경 감지 시 이벤트 발행")
    void checkWatch_WhenDigestChanged_PublishesUpdateEvent() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .tag("latest")
                .containerPattern("myapp-.*")
                .build();

        Container container = mock(Container.class);
        when(container.getId()).thenReturn("abc123");
        when(container.getNames()).thenReturn(new String[]{"/myapp-1"});
        when(container.getImageId()).thenReturn("sha256:olddigest");

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));

        when(ghcrClient.getLatestDigest("ghcr.io/myorg/myapp", "latest"))
                .thenReturn("sha256:newdigest");
        when(historyService.record(any())).thenReturn(null);

        poller.checkWatch(watch);

        ArgumentCaptor<ImageUpdateDetectedEvent> captor =
                ArgumentCaptor.forClass(ImageUpdateDetectedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        ImageUpdateDetectedEvent event = captor.getValue();
        assertThat(event.getContainerId()).isEqualTo("abc123");
        assertThat(event.getContainerName()).isEqualTo("myapp-1");
        assertThat(event.getCurrentDigest()).isEqualTo("sha256:olddigest");
        assertThat(event.getNewDigest()).isEqualTo("sha256:newdigest");
    }

    @Test
    @DisplayName("16. 새 digest 감지 시 DETECTED 이력을 저장한다")
    void checkWatch_WhenDigestChanged_RecordsDetectedHistory() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .tag("latest")
                .containerPattern("myapp-.*")
                .build();

        Container container = mock(Container.class);
        when(container.getId()).thenReturn("abc123");
        when(container.getNames()).thenReturn(new String[]{"/myapp-1"});
        when(container.getImageId()).thenReturn("sha256:old");

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));
        when(ghcrClient.getLatestDigest(anyString(), anyString())).thenReturn("sha256:new");
        when(historyService.record(any())).thenReturn(null);

        poller.checkWatch(watch);

        ArgumentCaptor<ImageUpdateHistoryEntity> captor =
                ArgumentCaptor.forClass(ImageUpdateHistoryEntity.class);
        verify(historyService).record(captor.capture());

        ImageUpdateHistoryEntity history = captor.getValue();
        assertThat(history.getWatchId()).isEqualTo(watch.getId());
        assertThat(history.getStatus()).isEqualTo(ImageUpdateHistoryEntity.Status.DETECTED);
        assertThat(history.getPreviousDigest()).isEqualTo("sha256:old");
        assertThat(history.getNewDigest()).isEqualTo("sha256:new");
        assertThat(history.getContainerName()).isEqualTo("myapp-1");
    }

    @Test
    @DisplayName("digest 동일 시 이벤트 미발행")
    void checkWatch_WhenDigestSame_DoesNotPublishEvent() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .tag("latest")
                .containerPattern("myapp-.*")
                .build();

        Container container = mock(Container.class);
        when(container.getNames()).thenReturn(new String[]{"/myapp-1"});
        when(container.getImageId()).thenReturn("sha256:samedigest");

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));
        when(ghcrClient.getLatestDigest("ghcr.io/myorg/myapp", "latest"))
                .thenReturn("sha256:samedigest");

        poller.checkWatch(watch);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("패턴 불일치 컨테이너는 무시")
    void checkWatch_WhenContainerNameNotMatchingPattern_Ignores() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .tag("latest")
                .containerPattern("myapp-.*")
                .build();

        Container container = mock(Container.class);
        when(container.getNames()).thenReturn(new String[]{"/other-service"});

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));
        when(ghcrClient.getLatestDigest(anyString(), anyString())).thenReturn("sha256:new");

        poller.checkWatch(watch);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("GHCR 조회 실패 시 이벤트 미발행")
    void checkWatch_WhenGhcrReturnsNull_DoesNotPublishEvent() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .tag("latest")
                .containerPattern("myapp-.*")
                .build();

        when(ghcrClient.getLatestDigest(anyString(), anyString())).thenReturn(null);

        poller.checkWatch(watch);

        verifyNoInteractions(eventPublisher, dockerClient);
    }
}
