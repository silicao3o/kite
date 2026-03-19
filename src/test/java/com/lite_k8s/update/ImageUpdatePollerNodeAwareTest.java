package com.lite_k8s.update;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import com.lite_k8s.node.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageUpdatePollerNodeAwareTest {

    @Mock private GhcrClient ghcrClient;
    @Mock private DockerClient localClient;
    @Mock private DockerClient remoteClient;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private NodeRegistry nodeRegistry;
    @Mock private NodeDockerClientFactory nodeClientFactory;
    @Mock private ListContainersCmd remoteListCmd;

    private ImageWatchProperties properties;
    private ImageUpdatePoller poller;
    private Node remoteNode;

    @BeforeEach
    void setUp() {
        properties = new ImageWatchProperties();
        properties.setEnabled(true);

        remoteNode = Node.builder()
                .id("node-b").name("vm-b").host("192.168.1.20").port(2375)
                .status(NodeStatus.HEALTHY).build();

        poller = new ImageUpdatePoller(properties, ghcrClient, localClient, eventPublisher,
                nodeRegistry, nodeClientFactory);
    }

    @Test
    @DisplayName("노드 등록 시 원격 노드의 컨테이너도 digest 비교")
    void checkWatch_WhenNodesRegistered_ShouldCheckRemoteContainers() {
        // given
        ImageWatchProperties.ImageWatch watch = new ImageWatchProperties.ImageWatch();
        watch.setImage("ghcr.io/foo/bar");
        watch.setTag("latest");
        watch.setContainerPattern("my-app");

        when(nodeRegistry.findAll()).thenReturn(List.of(remoteNode));
        when(nodeClientFactory.createClient(remoteNode)).thenReturn(remoteClient);
        when(ghcrClient.getLatestDigest(anyString(), anyString())).thenReturn("sha256:new");

        Container remoteContainer = mock(Container.class);
        when(remoteContainer.getId()).thenReturn("remote-c1");
        when(remoteContainer.getNames()).thenReturn(new String[]{"/my-app"});
        when(remoteContainer.getImageId()).thenReturn("sha256:old");

        when(remoteClient.listContainersCmd()).thenReturn(remoteListCmd);
        when(remoteListCmd.withShowAll(false)).thenReturn(remoteListCmd);
        when(remoteListCmd.exec()).thenReturn(List.of(remoteContainer));

        // when
        poller.checkWatch(watch);

        // then: 원격 노드 컨테이너에서 새 이미지 감지 → 이벤트 발행
        ArgumentCaptor<ImageUpdateDetectedEvent> captor = ArgumentCaptor.forClass(ImageUpdateDetectedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ImageUpdateDetectedEvent event = captor.getValue();
        assertThat(event.getNodeId()).isEqualTo("node-b");
        assertThat(event.getContainerId()).isEqualTo("remote-c1");

        // 로컬 클라이언트 사용 안 함
        verify(localClient, never()).listContainersCmd();
    }

    @Test
    @DisplayName("노드 미등록 시 로컬 클라이언트 폴백")
    void checkWatch_WhenNoNodesRegistered_ShouldUseLocalClient() {
        // given
        ImageWatchProperties.ImageWatch watch = new ImageWatchProperties.ImageWatch();
        watch.setImage("ghcr.io/foo/bar");
        watch.setTag("latest");
        watch.setContainerPattern("my-app");

        when(nodeRegistry.findAll()).thenReturn(List.of());
        when(ghcrClient.getLatestDigest(anyString(), anyString())).thenReturn("sha256:new");

        ListContainersCmd localListCmd = mock(ListContainersCmd.class);
        when(localClient.listContainersCmd()).thenReturn(localListCmd);
        when(localListCmd.withShowAll(false)).thenReturn(localListCmd);
        when(localListCmd.exec()).thenReturn(List.of());

        // when
        poller.checkWatch(watch);

        // then: 로컬 클라이언트 사용
        verify(localClient).listContainersCmd();
        verify(remoteClient, never()).listContainersCmd();
    }
}
