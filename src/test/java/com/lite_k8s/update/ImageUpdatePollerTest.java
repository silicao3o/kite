package com.lite_k8s.update;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.command.ListContainersCmd;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
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
    private ListAppender<ILoggingEvent> logAppender;
    private Logger pollerLogger;

    @BeforeEach
    void setUp() {
        properties = new ImageWatchProperties();
        poller = new ImageUpdatePoller(properties, watchService, ghcrClient, dockerClient,
                eventPublisher, historyService);

        pollerLogger = (Logger) LoggerFactory.getLogger(ImageUpdatePoller.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        pollerLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        pollerLogger.detachAppender(logAppender);
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
        when(container.getImage()).thenReturn("ghcr.io/myorg/myapp:latest");
        when(container.getImageId()).thenReturn("sha256:olddigest");

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));

        when(ghcrClient.getLatestDigest(eq("ghcr.io/myorg/myapp"), eq("latest"), any()))
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
    @DisplayName("새 digest 감지 시 DETECTED 이력을 저장한다")
    void checkWatch_WhenDigestChanged_RecordsDetectedHistory() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .tag("latest")
                .containerPattern("myapp-.*")
                .build();

        Container container = mock(Container.class);
        when(container.getId()).thenReturn("abc123");
        when(container.getNames()).thenReturn(new String[]{"/myapp-1"});
        when(container.getImage()).thenReturn("ghcr.io/myorg/myapp:latest");
        when(container.getImageId()).thenReturn("sha256:old");

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any())).thenReturn("sha256:new");
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
        when(container.getImage()).thenReturn("ghcr.io/myorg/myapp:latest");
        when(container.getImageId()).thenReturn("sha256:samedigest");

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));
        when(ghcrClient.getLatestDigest(eq("ghcr.io/myorg/myapp"), eq("latest"), any()))
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
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any())).thenReturn("sha256:new");

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

        when(ghcrClient.getLatestDigest(anyString(), anyString(), any())).thenReturn(null);

        poller.checkWatch(watch);

        verifyNoInteractions(eventPublisher, dockerClient);
    }

    @Test
    @DisplayName("레지스트리에 토큰이 있으면 해당 토큰으로 digest를 조회한다")
    void checkWatch_WithRegistryToken_UsesRegistryToken() {
        com.lite_k8s.envprofile.ImageRegistry registry = com.lite_k8s.envprofile.ImageRegistry.builder()
                .image("ghcr.io/myorg/myapp").ghcrToken("ghp_registry_token").build();
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .tag("latest")
                .containerPattern("myapp-.*")
                .imageRegistry(registry)
                .build();

        when(ghcrClient.getLatestDigest("ghcr.io/myorg/myapp", "latest", "ghp_registry_token"))
                .thenReturn(null);

        poller.checkWatch(watch);

        verify(ghcrClient).getLatestDigest("ghcr.io/myorg/myapp", "latest", "ghp_registry_token");
    }

    @Test
    @DisplayName("와치에 ghcrToken이 없으면 null로 전달하여 글로벌 폴백한다")
    void checkWatch_WithoutWatchToken_PassesNull() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .tag("latest")
                .containerPattern("myapp-.*")
                .build();

        when(ghcrClient.getLatestDigest("ghcr.io/myorg/myapp", "latest", null))
                .thenReturn(null);

        poller.checkWatch(watch);

        verify(ghcrClient).getLatestDigest("ghcr.io/myorg/myapp", "latest", null);
    }

    @Test
    @DisplayName("glob 패턴 '*quvi*'가 'admin-quvi' 컨테이너에 매칭된다")
    void checkWatch_GlobPatternWithLeadingStar_Matches() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/quvi")
                .tag("latest")
                .containerPattern("*quvi*")
                .build();

        Container container = mock(Container.class);
        when(container.getId()).thenReturn("abc123");
        when(container.getNames()).thenReturn(new String[]{"/admin-quvi"});
        when(container.getImage()).thenReturn("ghcr.io/myorg/quvi:latest");
        when(container.getImageId()).thenReturn("sha256:old");

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any())).thenReturn("sha256:new");
        when(historyService.record(any())).thenReturn(null);

        poller.checkWatch(watch);

        verify(eventPublisher).publishEvent(any(ImageUpdateDetectedEvent.class));
    }

    @Test
    @DisplayName("glob 패턴 'engine*'가 'engine' 컨테이너에 매칭된다")
    void checkWatch_GlobPatternWithTrailingStar_Matches() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/engine")
                .tag("latest")
                .containerPattern("engine*")
                .build();

        Container container = mock(Container.class);
        when(container.getId()).thenReturn("abc123");
        when(container.getNames()).thenReturn(new String[]{"/engine"});
        when(container.getImage()).thenReturn("ghcr.io/myorg/engine:latest");
        when(container.getImageId()).thenReturn("sha256:old");

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any())).thenReturn("sha256:new");
        when(historyService.record(any())).thenReturn(null);

        poller.checkWatch(watch);

        verify(eventPublisher).publishEvent(any(ImageUpdateDetectedEvent.class));
    }

    @Test
    @DisplayName("glob 패턴 '*quvi*'가 'nginx' 컨테이너에는 매칭되지 않는다")
    void checkWatch_GlobPatternNoMatch_Ignores() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/quvi")
                .tag("latest")
                .containerPattern("*quvi*")
                .build();

        Container container = mock(Container.class);
        when(container.getNames()).thenReturn(new String[]{"/nginx"});

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any())).thenReturn("sha256:new");

        poller.checkWatch(watch);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("기존 regex 패턴 'myapp-.*'도 계속 동작한다")
    void checkWatch_RegexPatternStillWorks() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .tag("latest")
                .containerPattern("myapp-.*")
                .build();

        Container container = mock(Container.class);
        when(container.getId()).thenReturn("abc123");
        when(container.getNames()).thenReturn(new String[]{"/myapp-prod"});
        when(container.getImage()).thenReturn("ghcr.io/myorg/myapp:latest");
        when(container.getImageId()).thenReturn("sha256:old");

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any())).thenReturn("sha256:new");
        when(historyService.record(any())).thenReturn(null);

        poller.checkWatch(watch);

        verify(eventPublisher).publishEvent(any(ImageUpdateDetectedEvent.class));
    }

    @Test
    @DisplayName("triggerAll은 모든 활성 와치를 체크한다")
    void triggerAll_ChecksAllEnabledWatches() {
        properties.setEnabled(true);

        ImageWatchEntity watch1 = ImageWatchEntity.builder().image("ghcr.io/org/app1").build();
        ImageWatchEntity watch2 = ImageWatchEntity.builder().image("ghcr.io/org/app2").build();
        when(watchService.findEnabled()).thenReturn(List.of(watch1, watch2));
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any())).thenReturn(null);

        poller.triggerAll();

        verify(ghcrClient).getLatestDigest("ghcr.io/org/app1", "latest", null);
        verify(ghcrClient).getLatestDigest("ghcr.io/org/app2", "latest", null);
    }

    @Test
    @DisplayName("checkWatch 진입 시 image:tag를 포함한 INFO 로그를 남긴다")
    void checkWatch_LogsEntryWithImageAndTag() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .tag("v1.2")
                .containerPattern("myapp-.*")
                .build();
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any())).thenReturn(null);

        poller.checkWatch(watch);

        assertThat(logAppender.list)
                .filteredOn(e -> e.getLevel() == Level.INFO)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(msg -> msg.contains("ghcr.io/myorg/myapp") && msg.contains("v1.2"));
    }

    @Test
    @DisplayName("digest 동일 시 변화 없음 요약 로그를 남긴다")
    void checkWatch_WhenDigestSame_LogsNoChangeSummary() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .tag("latest")
                .containerPattern("myapp-.*")
                .build();

        Container container = mock(Container.class);
        when(container.getNames()).thenReturn(new String[]{"/myapp-1"});
        when(container.getImage()).thenReturn("ghcr.io/myorg/myapp:latest");
        when(container.getImageId()).thenReturn("sha256:samedigest");

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any()))
                .thenReturn("sha256:samedigest");

        poller.checkWatch(watch);

        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(msg -> msg.contains("변화 없음") || msg.contains("최신"));
    }

    @Test
    @DisplayName("매칭 컨테이너가 없으면 매칭 0개 로그를 남긴다")
    void checkWatch_WhenNoMatchingContainer_LogsZeroMatch() {
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
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any())).thenReturn("sha256:new");

        poller.checkWatch(watch);

        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(msg -> msg.contains("매칭") && msg.contains("0"));
    }

    @Test
    @DisplayName("컨테이너 이미지 레포가 watch image와 다르면 업데이트 대상에서 제외한다")
    void checkWatch_WhenContainerImageRepoDiffers_SkipsContainer() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/daquv-qv/chat-quvi")
                .tag("latest")
                .containerPattern("chat-quvi.*")
                .build();

        // nginx 사이드카 — 이름은 매칭되지만 이미지가 완전히 다름
        Container nginx = mock(Container.class);
        when(nginx.getNames()).thenReturn(new String[]{"/chat-quvi-qvc-nginx"});
        when(nginx.getImage()).thenReturn("nginx:alpine");

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(nginx));
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any()))
                .thenReturn("sha256:chatquvinew");

        poller.checkWatch(watch);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("컨테이너 이미지 레포가 같으면(태그 다름) 업데이트 대상으로 인식한다")
    void checkWatch_WhenSameRepoDifferentTag_StillMatches() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/daquv-qv/chat-quvi")
                .tag("latest")
                .containerPattern("chat-quvi.*")
                .build();

        Container app = mock(Container.class);
        when(app.getId()).thenReturn("abc");
        when(app.getNames()).thenReturn(new String[]{"/chat-quvi-qvc"});
        when(app.getImage()).thenReturn("ghcr.io/daquv-qv/chat-quvi:latest");
        when(app.getImageId()).thenReturn("sha256:old");

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(app));
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any()))
                .thenReturn("sha256:new");
        when(historyService.record(any())).thenReturn(null);

        poller.checkWatch(watch);

        verify(eventPublisher).publishEvent(any(ImageUpdateDetectedEvent.class));
    }

    @Test
    @DisplayName("glob 패턴 '*quvi*'는 glob→regex 변환으로 정상 매칭된다 (폴백 없이)")
    void checkWatch_GlobPattern_ConvertsToRegexWithoutFallback() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .tag("latest")
                .containerPattern("*quvi*")
                .build();

        Container container = mock(Container.class);
        when(container.getId()).thenReturn("abc");
        when(container.getNames()).thenReturn(new String[]{"/myapp-quvi-1"});
        when(container.getImage()).thenReturn("ghcr.io/myorg/myapp:latest");
        when(container.getImageId()).thenReturn("sha256:old");

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any()))
                .thenReturn("sha256:new");
        when(historyService.record(any())).thenReturn(null);

        // glob→regex 변환으로 WARN 없이 정상 매칭
        poller.checkWatch(watch);

        verify(eventPublisher).publishEvent(any(ImageUpdateDetectedEvent.class));
        assertThat(logAppender.list)
                .filteredOn(e -> e.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(msg -> msg.contains("폴백"));
    }

    @Test
    @DisplayName("checkWatch(id)는 ID로 와치를 찾아 체크한다")
    void checkWatchById_FindsAndChecks() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .build();
        when(watchService.findById(watch.getId())).thenReturn(java.util.Optional.of(watch));
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any())).thenReturn(null);

        poller.checkWatch(watch.getId());

        verify(ghcrClient).getLatestDigest("ghcr.io/org/app", "latest", null);
    }
}
