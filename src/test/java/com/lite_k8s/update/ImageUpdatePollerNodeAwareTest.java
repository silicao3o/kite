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
    @Mock private ImageWatchService watchService;
    @Mock private ImageUpdateHistoryService historyService;

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

        poller = new ImageUpdatePoller(properties, watchService, ghcrClient, localClient,
                eventPublisher, historyService, nodeRegistry, nodeClientFactory);
    }

    @Test
    @DisplayName("노드 등록 시 원격 노드의 컨테이너도 digest 비교")
    void checkWatch_WhenNodesRegistered_ShouldCheckRemoteContainers() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/foo/bar")
                .tag("latest")
                .containerPattern("my-app")
                .build();

        when(nodeRegistry.findAll()).thenReturn(List.of(remoteNode));
        when(nodeClientFactory.createClient(remoteNode)).thenReturn(remoteClient);
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any())).thenReturn("sha256:new");
        when(historyService.record(any())).thenReturn(null);

        Container remoteContainer = mock(Container.class);
        when(remoteContainer.getId()).thenReturn("remote-c1");
        when(remoteContainer.getNames()).thenReturn(new String[]{"/my-app"});
        when(remoteContainer.getImage()).thenReturn("ghcr.io/foo/bar:latest");
        when(remoteContainer.getImageId()).thenReturn("sha256:old");

        when(remoteClient.listContainersCmd()).thenReturn(remoteListCmd);
        when(remoteListCmd.withShowAll(false)).thenReturn(remoteListCmd);
        when(remoteListCmd.exec()).thenReturn(List.of(remoteContainer));

        poller.checkWatch(watch);

        ArgumentCaptor<ImageUpdateDetectedEvent> captor = ArgumentCaptor.forClass(ImageUpdateDetectedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ImageUpdateDetectedEvent event = captor.getValue();
        assertThat(event.getNodeId()).isEqualTo("node-b");
        assertThat(event.getContainerId()).isEqualTo("remote-c1");

        verify(localClient, never()).listContainersCmd();
    }

    @Test
    @DisplayName("노드 미등록 시 로컬 클라이언트 폴백")
    void checkWatch_WhenNoNodesRegistered_ShouldUseLocalClient() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/foo/bar")
                .tag("latest")
                .containerPattern("my-app")
                .build();

        when(nodeRegistry.findAll()).thenReturn(List.of());
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any())).thenReturn("sha256:new");

        ListContainersCmd localListCmd = mock(ListContainersCmd.class);
        when(localClient.listContainersCmd()).thenReturn(localListCmd);
        when(localListCmd.withShowAll(false)).thenReturn(localListCmd);
        when(localListCmd.exec()).thenReturn(List.of());

        poller.checkWatch(watch);

        verify(localClient).listContainersCmd();
        verify(remoteClient, never()).listContainersCmd();
    }

    @Test
    @DisplayName("nodeNames에 특정 노드가 지정되면 해당 노드들만 체크한다")
    void checkWatch_WithNodeNames_OnlyChecksTargetNodes() {
        Node nodeA = Node.builder()
                .id("node-a").name("vm-a").host("192.168.1.10").port(2375)
                .status(NodeStatus.HEALTHY).build();
        Node nodeC = Node.builder()
                .id("node-c").name("vm-c").host("192.168.1.30").port(2375)
                .status(NodeStatus.HEALTHY).build();

        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/foo/bar")
                .tag("latest")
                .containerPattern("my-app")
                .nodeNames(List.of("vm-b", "vm-c"))  // vm-a는 제외
                .build();

        when(nodeRegistry.findAll()).thenReturn(List.of(nodeA, remoteNode, nodeC));
        when(nodeClientFactory.createClient(remoteNode)).thenReturn(remoteClient);
        DockerClient nodeCClient = mock(DockerClient.class);
        when(nodeClientFactory.createClient(nodeC)).thenReturn(nodeCClient);
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any())).thenReturn("sha256:new");

        when(remoteClient.listContainersCmd()).thenReturn(remoteListCmd);
        when(remoteListCmd.withShowAll(false)).thenReturn(remoteListCmd);
        when(remoteListCmd.exec()).thenReturn(List.of());
        ListContainersCmd nodeCListCmd = mock(ListContainersCmd.class);
        when(nodeCClient.listContainersCmd()).thenReturn(nodeCListCmd);
        when(nodeCListCmd.withShowAll(false)).thenReturn(nodeCListCmd);
        when(nodeCListCmd.exec()).thenReturn(List.of());

        poller.checkWatch(watch);

        // nodeA(vm-a)는 체크하지 않음
        verify(nodeClientFactory, never()).createClient(nodeA);
        // remoteNode(vm-b)와 nodeC(vm-c)만 체크
        verify(nodeClientFactory).createClient(remoteNode);
        verify(nodeClientFactory).createClient(nodeC);
    }

    @Test
    @DisplayName("nodeNames가 비어있으면 모든 노드를 체크한다")
    void checkWatch_WithEmptyNodeNames_ChecksAllNodes() {
        Node nodeA = Node.builder()
                .id("node-a").name("vm-a").host("192.168.1.10").port(2375)
                .status(NodeStatus.HEALTHY).build();
        DockerClient nodeAClient = mock(DockerClient.class);
        ListContainersCmd nodeAListCmd = mock(ListContainersCmd.class);

        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/foo/bar")
                .tag("latest")
                .containerPattern("my-app")
                .build(); // nodeNames = empty list

        when(nodeRegistry.findAll()).thenReturn(List.of(nodeA, remoteNode));
        when(nodeClientFactory.createClient(nodeA)).thenReturn(nodeAClient);
        when(nodeClientFactory.createClient(remoteNode)).thenReturn(remoteClient);
        when(ghcrClient.getLatestDigest(anyString(), anyString(), any())).thenReturn("sha256:new");

        when(nodeAClient.listContainersCmd()).thenReturn(nodeAListCmd);
        when(nodeAListCmd.withShowAll(false)).thenReturn(nodeAListCmd);
        when(nodeAListCmd.exec()).thenReturn(List.of());
        when(remoteClient.listContainersCmd()).thenReturn(remoteListCmd);
        when(remoteListCmd.withShowAll(false)).thenReturn(remoteListCmd);
        when(remoteListCmd.exec()).thenReturn(List.of());

        poller.checkWatch(watch);

        // 둘 다 체크
        verify(nodeClientFactory).createClient(nodeA);
        verify(nodeClientFactory).createClient(remoteNode);
    }
}
