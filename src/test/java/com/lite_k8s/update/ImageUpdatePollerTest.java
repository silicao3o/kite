package com.lite_k8s.update;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageUpdatePollerTest {

    @Mock
    private GhcrClient ghcrClient;
    @Mock
    private DockerClient dockerClient;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ListContainersCmd listContainersCmd;

    private ImageWatchProperties properties;
    private ImageUpdatePoller poller;

    @BeforeEach
    void setUp() {
        properties = new ImageWatchProperties();
        poller = new ImageUpdatePoller(properties, ghcrClient, dockerClient, eventPublisher);
    }

    @Test
    @DisplayName("비활성화 시 폴링 스킵")
    void pollAll_WhenDisabled_DoesNothing() {
        properties.setEnabled(false);

        poller.pollAll();

        verifyNoInteractions(ghcrClient, dockerClient);
    }

    @Test
    @DisplayName("digest 변경 감지 시 이벤트 발행")
    void checkWatch_WhenDigestChanged_PublishesUpdateEvent() {
        // given
        ImageWatchProperties.ImageWatch watch = new ImageWatchProperties.ImageWatch();
        watch.setImage("ghcr.io/myorg/myapp");
        watch.setTag("latest");
        watch.setContainerPattern("myapp-.*");
        properties.setEnabled(true);
        properties.setWatches(List.of(watch));

        Container container = mock(Container.class);
        when(container.getId()).thenReturn("abc123");
        when(container.getNames()).thenReturn(new String[]{"/myapp-1"});
        when(container.getImageId()).thenReturn("sha256:olddigest");

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));

        when(ghcrClient.getLatestDigest("ghcr.io/myorg/myapp", "latest"))
                .thenReturn("sha256:newdigest");

        // when
        poller.checkWatch(watch);

        // then
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
    @DisplayName("digest 동일 시 이벤트 미발행")
    void checkWatch_WhenDigestSame_DoesNotPublishEvent() {
        // given
        ImageWatchProperties.ImageWatch watch = new ImageWatchProperties.ImageWatch();
        watch.setImage("ghcr.io/myorg/myapp");
        watch.setTag("latest");
        watch.setContainerPattern("myapp-.*");

        Container container = mock(Container.class);
        when(container.getNames()).thenReturn(new String[]{"/myapp-1"});
        when(container.getImageId()).thenReturn("sha256:samedigest");

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));

        when(ghcrClient.getLatestDigest("ghcr.io/myorg/myapp", "latest"))
                .thenReturn("sha256:samedigest");

        // when
        poller.checkWatch(watch);

        // then
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("패턴 불일치 컨테이너는 무시")
    void checkWatch_WhenContainerNameNotMatchingPattern_Ignores() {
        // given
        ImageWatchProperties.ImageWatch watch = new ImageWatchProperties.ImageWatch();
        watch.setImage("ghcr.io/myorg/myapp");
        watch.setTag("latest");
        watch.setContainerPattern("myapp-.*");

        Container container = mock(Container.class);
        when(container.getNames()).thenReturn(new String[]{"/other-service"});

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));

        when(ghcrClient.getLatestDigest(anyString(), anyString())).thenReturn("sha256:new");

        // when
        poller.checkWatch(watch);

        // then - 패턴 불일치로 이벤트 없음
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("GHCR 조회 실패 시 이벤트 미발행")
    void checkWatch_WhenGhcrReturnsNull_DoesNotPublishEvent() {
        // given
        ImageWatchProperties.ImageWatch watch = new ImageWatchProperties.ImageWatch();
        watch.setImage("ghcr.io/myorg/myapp");
        watch.setTag("latest");
        watch.setContainerPattern("myapp-.*");

        when(ghcrClient.getLatestDigest(anyString(), anyString())).thenReturn(null);

        // when
        poller.checkWatch(watch);

        // then - GHCR 실패로 조기 리턴, 이벤트 없음
        verifyNoInteractions(eventPublisher, dockerClient);
    }
}
