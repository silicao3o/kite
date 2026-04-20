package com.lite_k8s.update;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RollingUpdateServiceTest {

    @Mock private DockerClient dockerClient;
    @Mock private ContainerRecreator recreator;
    @Mock private NodeRegistry nodeRegistry;
    @Mock private NodeDockerClientFactory nodeClientFactory;
    @Mock private ImageUpdateHistoryService historyService;

    private RollingUpdateService service;

    @BeforeEach
    void setUp() {
        service = new RollingUpdateService(dockerClient, recreator, nodeRegistry,
                nodeClientFactory, historyService);
    }

    @Test
    @DisplayName("maxUnavailable=1이면 컨테이너 하나씩 순차 업데이트")
    void executeRollingUpdate_WithMaxUnavailable1_UpdatesSequentially() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .tag("latest")
                .containerPattern("myapp-.*")
                .maxUnavailable(1)
                .build();

        Container c1 = mockContainer("c1", "/myapp-1");
        Container c2 = mockContainer("c2", "/myapp-2");

        when(recreator.recreate(any(), any(), any(), any())).thenReturn(true);
        when(historyService.record(any())).thenReturn(null);

        List<UpdateResult> results = service.executeRollingUpdate(List.of(c1, c2), watch, "sha256:new");

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(UpdateResult::isSuccess);
        verify(recreator, times(2)).recreate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("maxUnavailable=2이면 2개 동시 업데이트")
    void executeRollingUpdate_WithMaxUnavailable2_UpdatesTwoAtATime() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .tag("latest")
                .maxUnavailable(2)
                .build();

        Container c1 = mockContainer("c1", "/myapp-1");
        Container c2 = mockContainer("c2", "/myapp-2");
        Container c3 = mockContainer("c3", "/myapp-3");

        when(recreator.recreate(any(), any(), any(), any())).thenReturn(true);
        when(historyService.record(any())).thenReturn(null);

        List<UpdateResult> results = service.executeRollingUpdate(List.of(c1, c2, c3), watch, "sha256:new");

        assertThat(results).hasSize(3);
        verify(recreator, times(3)).recreate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("recreate 실패 시 결과에 실패 표시")
    void executeRollingUpdate_WhenRecreateFails_RecordsFailure() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .tag("latest")
                .maxUnavailable(1)
                .build();

        Container c1 = mockContainer("c1", "/myapp-1");

        when(recreator.recreate(any(), any(), any(), any())).thenReturn(false);
        when(historyService.record(any())).thenReturn(null);

        List<UpdateResult> results = service.executeRollingUpdate(List.of(c1), watch, "sha256:new");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isSuccess()).isFalse();
        assertThat(results.get(0).getContainerName()).isEqualTo("myapp-1");
    }

    @Test
    @DisplayName("17. 업데이트 성공/실패 시 이력을 저장한다")
    void executeRollingUpdate_RecordsHistory() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .tag("latest")
                .maxUnavailable(1)
                .build();

        Container c1 = mockContainer("c1", "/myapp-1");
        Container c2 = mockContainer("c2", "/myapp-2");

        when(recreator.recreate(eq("c1"), any(), any(), any())).thenReturn(true);
        when(recreator.recreate(eq("c2"), any(), any(), any())).thenReturn(false);
        when(historyService.record(any())).thenReturn(null);

        service.executeRollingUpdate(List.of(c1, c2), watch, "sha256:new");

        ArgumentCaptor<ImageUpdateHistoryEntity> captor =
                ArgumentCaptor.forClass(ImageUpdateHistoryEntity.class);
        verify(historyService, times(2)).record(captor.capture());

        List<ImageUpdateHistoryEntity> histories = captor.getAllValues();
        assertThat(histories.get(0).getStatus()).isEqualTo(ImageUpdateHistoryEntity.Status.SUCCESS);
        assertThat(histories.get(0).getContainerName()).isEqualTo("myapp-1");
        assertThat(histories.get(1).getStatus()).isEqualTo(ImageUpdateHistoryEntity.Status.FAILED);
        assertThat(histories.get(1).getContainerName()).isEqualTo("myapp-2");
    }

    @Test
    @DisplayName("빈 컨테이너 목록이면 아무것도 하지 않음")
    void executeRollingUpdate_WithEmptyList_DoesNothing() {
        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .maxUnavailable(1)
                .build();

        List<UpdateResult> results = service.executeRollingUpdate(List.of(), watch, "sha256:new");

        assertThat(results).isEmpty();
        verifyNoInteractions(recreator);
    }

    @Test
    @DisplayName("nodeId가 있으면 해당 노드 클라이언트로 컨테이너 검색")
    void onImageUpdateDetected_WithNodeId_UsesNodeClient() {
        String nodeId = "node-res";
        Node node = mock(Node.class);
        DockerClient nodeClient = mock(DockerClient.class);
        ListContainersCmd listCmd = mock(ListContainersCmd.class);

        Container target = mockContainer("c1", "/engine");

        when(nodeRegistry.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeClientFactory.createClient(node)).thenReturn(nodeClient);
        when(nodeClient.listContainersCmd()).thenReturn(listCmd);
        when(listCmd.withShowAll(false)).thenReturn(listCmd);
        when(listCmd.exec()).thenReturn(List.of(target));
        when(recreator.recreate(any(), any(), any(), any())).thenReturn(true);
        when(historyService.record(any())).thenReturn(null);

        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/engine")
                .tag("latest")
                .containerPattern("engine")
                .maxUnavailable(1)
                .build();

        ImageUpdateDetectedEvent event = new ImageUpdateDetectedEvent(
                "c1", "engine", "ghcr.io/myorg/engine", "latest",
                "sha256:old", "sha256:new", watch, nodeId);

        service.onImageUpdateDetected(event);

        verify(dockerClient, never()).listContainersCmd();
        verify(nodeClient).listContainersCmd();
        // nodeId를 recreator에 전달해야 원격 노드에서 컨테이너를 찾을 수 있다
        verify(recreator).recreate(eq("c1"), any(), eq("sha256:new"), eq(nodeId));
    }

    @Test
    @DisplayName("nodeId가 null이면 로컬 dockerClient로 컨테이너 검색")
    void onImageUpdateDetected_WithoutNodeId_UsesLocalClient() {
        ListContainersCmd listCmd = mock(ListContainersCmd.class);
        Container target = mockContainer("c1", "/myapp-1");

        when(dockerClient.listContainersCmd()).thenReturn(listCmd);
        when(listCmd.withShowAll(false)).thenReturn(listCmd);
        when(listCmd.exec()).thenReturn(List.of(target));
        when(recreator.recreate(any(), any(), any(), any())).thenReturn(true);
        when(historyService.record(any())).thenReturn(null);

        ImageWatchEntity watch = ImageWatchEntity.builder()
                .image("ghcr.io/myorg/myapp")
                .tag("latest")
                .containerPattern("myapp-.*")
                .maxUnavailable(1)
                .build();

        ImageUpdateDetectedEvent event = new ImageUpdateDetectedEvent(
                "c1", "myapp-1", "ghcr.io/myorg/myapp", "latest",
                "sha256:old", "sha256:new", watch);

        service.onImageUpdateDetected(event);

        verify(dockerClient).listContainersCmd();
        verifyNoInteractions(nodeRegistry, nodeClientFactory);
    }

    private Container mockContainer(String id, String name) {
        Container c = mock(Container.class);
        when(c.getId()).thenReturn(id);
        when(c.getNames()).thenReturn(new String[]{name});
        when(c.getImageId()).thenReturn("sha256:old");
        return c;
    }
}
