package com.lite_k8s.update;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.exception.DockerException;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private RollingUpdateService service;

    @BeforeEach
    void setUp() {
        service = new RollingUpdateService(dockerClient, recreator, nodeRegistry, nodeClientFactory);
    }

    @Test
    @DisplayName("maxUnavailable=1이면 컨테이너 하나씩 순차 업데이트")
    void executeRollingUpdate_WithMaxUnavailable1_UpdatesSequentially() {
        // given
        ImageWatchProperties.ImageWatch watch = new ImageWatchProperties.ImageWatch();
        watch.setImage("ghcr.io/myorg/myapp");
        watch.setTag("latest");
        watch.setContainerPattern("myapp-.*");
        watch.setMaxUnavailable(1);

        Container c1 = mockContainer("c1", "/myapp-1");
        Container c2 = mockContainer("c2", "/myapp-2");

        when(recreator.recreate(any(), any(), any())).thenReturn(true);

        // when
        List<UpdateResult> results = service.executeRollingUpdate(List.of(c1, c2), watch, "sha256:new");

        // then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(UpdateResult::isSuccess);

        // recreate 순차 호출 확인
        verify(recreator, times(2)).recreate(any(), any(), any());
    }

    @Test
    @DisplayName("maxUnavailable=2이면 2개 동시 업데이트")
    void executeRollingUpdate_WithMaxUnavailable2_UpdatesTwoAtATime() {
        // given
        ImageWatchProperties.ImageWatch watch = new ImageWatchProperties.ImageWatch();
        watch.setImage("ghcr.io/myorg/myapp");
        watch.setTag("latest");
        watch.setMaxUnavailable(2);

        Container c1 = mockContainer("c1", "/myapp-1");
        Container c2 = mockContainer("c2", "/myapp-2");
        Container c3 = mockContainer("c3", "/myapp-3");

        when(recreator.recreate(any(), any(), any())).thenReturn(true);

        // when
        List<UpdateResult> results = service.executeRollingUpdate(List.of(c1, c2, c3), watch, "sha256:new");

        // then
        assertThat(results).hasSize(3);
        verify(recreator, times(3)).recreate(any(), any(), any());
    }

    @Test
    @DisplayName("recreate 실패 시 롤백 수행 후 결과에 실패 표시")
    void executeRollingUpdate_WhenRecreateFails_RecordsFailure() {
        // given
        ImageWatchProperties.ImageWatch watch = new ImageWatchProperties.ImageWatch();
        watch.setImage("ghcr.io/myorg/myapp");
        watch.setTag("latest");
        watch.setMaxUnavailable(1);

        Container c1 = mockContainer("c1", "/myapp-1");

        when(recreator.recreate(any(), any(), any())).thenReturn(false);

        // when
        List<UpdateResult> results = service.executeRollingUpdate(List.of(c1), watch, "sha256:new");

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).isSuccess()).isFalse();
        assertThat(results.get(0).getContainerName()).isEqualTo("myapp-1");
    }

    @Test
    @DisplayName("빈 컨테이너 목록이면 아무것도 하지 않음")
    void executeRollingUpdate_WithEmptyList_DoesNothing() {
        // given
        ImageWatchProperties.ImageWatch watch = new ImageWatchProperties.ImageWatch();
        watch.setMaxUnavailable(1);

        // when
        List<UpdateResult> results = service.executeRollingUpdate(List.of(), watch, "sha256:new");

        // then
        assertThat(results).isEmpty();
        verifyNoInteractions(recreator);
    }

    @Test
    @DisplayName("nodeId가 있으면 해당 노드 클라이언트로 컨테이너 검색")
    void onImageUpdateDetected_WithNodeId_UsesNodeClient() {
        // given
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
        when(recreator.recreate(any(), any(), any())).thenReturn(true);

        ImageWatchProperties.ImageWatch watch = new ImageWatchProperties.ImageWatch();
        watch.setImage("ghcr.io/myorg/engine");
        watch.setTag("latest");
        watch.setContainerPattern("engine");
        watch.setMaxUnavailable(1);

        ImageUpdateDetectedEvent event = new ImageUpdateDetectedEvent(
                "c1", "engine", "ghcr.io/myorg/engine", "latest",
                "sha256:old", "sha256:new", watch, nodeId);

        // when
        service.onImageUpdateDetected(event);

        // then: 로컬 dockerClient는 사용하지 않음
        verify(dockerClient, never()).listContainersCmd();
        // nodeClient를 통해 컨테이너 조회
        verify(nodeClient).listContainersCmd();
        // recreate 호출 확인
        verify(recreator).recreate(eq("c1"), any(), eq("sha256:new"));
    }

    @Test
    @DisplayName("nodeId가 null이면 로컬 dockerClient로 컨테이너 검색")
    void onImageUpdateDetected_WithoutNodeId_UsesLocalClient() {
        // given
        ListContainersCmd listCmd = mock(ListContainersCmd.class);
        Container target = mockContainer("c1", "/myapp-1");

        when(dockerClient.listContainersCmd()).thenReturn(listCmd);
        when(listCmd.withShowAll(false)).thenReturn(listCmd);
        when(listCmd.exec()).thenReturn(List.of(target));
        when(recreator.recreate(any(), any(), any())).thenReturn(true);

        ImageWatchProperties.ImageWatch watch = new ImageWatchProperties.ImageWatch();
        watch.setImage("ghcr.io/myorg/myapp");
        watch.setTag("latest");
        watch.setContainerPattern("myapp-.*");
        watch.setMaxUnavailable(1);

        ImageUpdateDetectedEvent event = new ImageUpdateDetectedEvent(
                "c1", "myapp-1", "ghcr.io/myorg/myapp", "latest",
                "sha256:old", "sha256:new", watch); // nodeId = null

        // when
        service.onImageUpdateDetected(event);

        // then: 로컬 dockerClient 사용
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
