package com.lite_k8s.desired;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StateReconcilerTest {

    @Mock private DockerClient dockerClient;
    @Mock private ContainerFactory containerFactory;
    @Mock private NodeRegistry nodeRegistry;
    @Mock private NodeDockerClientFactory nodeClientFactory;
    @Mock private ListContainersCmd listContainersCmd;

    private DesiredStateProperties properties;
    private StateReconciler reconciler;

    @BeforeEach
    void setUp() {
        properties = new DesiredStateProperties();
        reconciler = new StateReconciler(properties, dockerClient, containerFactory, nodeRegistry, nodeClientFactory);

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
    }

    @Test
    @DisplayName("비활성화 시 아무것도 하지 않음")
    void reconcile_WhenDisabled_DoesNothing() {
        properties.setEnabled(false);

        reconciler.reconcile();

        verifyNoInteractions(dockerClient, containerFactory);
    }

    @Test
    @DisplayName("실제 컨테이너 수 < replicas → 부족한 만큼 생성")
    void reconcile_WhenActualLessThanDesired_CreatesContainers() {
        // desired: 3, actual: 1
        DesiredStateProperties.ServiceSpec spec = serviceSpec("demo-api", "ghcr.io/myorg/demo-api:latest", 3);
        properties.setEnabled(true);
        properties.setServices(List.of(spec));

        Container running = mockContainer("demo-api-1", "running");
        when(listContainersCmd.exec()).thenReturn(List.of(running));

        reconciler.reconcile();

        // 2개 더 생성
        verify(containerFactory, times(2)).create(eq(spec), anyInt());
    }

    @Test
    @DisplayName("실제 컨테이너 수 == replicas → 아무것도 안 함")
    void reconcile_WhenActualEqualsDesired_DoesNothing() {
        DesiredStateProperties.ServiceSpec spec = serviceSpec("demo-api", "ghcr.io/myorg/demo-api:latest", 2);
        properties.setEnabled(true);
        properties.setServices(List.of(spec));

        Container c1 = mockContainer("demo-api-1", "running");
        Container c2 = mockContainer("demo-api-2", "running");
        when(listContainersCmd.exec()).thenReturn(List.of(c1, c2));

        reconciler.reconcile();

        verifyNoInteractions(containerFactory);
    }

    @Test
    @DisplayName("실제 컨테이너 수 > replicas → 초과분 제거")
    void reconcile_WhenActualMoreThanDesired_RemovesExcess() {
        DesiredStateProperties.ServiceSpec spec = serviceSpec("demo-api", "ghcr.io/myorg/demo-api:latest", 1);
        properties.setEnabled(true);
        properties.setServices(List.of(spec));

        Container c1 = mockContainer("demo-api-1", "running");
        Container c2 = mockContainer("demo-api-2", "running");
        Container c3 = mockContainer("demo-api-3", "running");
        when(listContainersCmd.exec()).thenReturn(List.of(c1, c2, c3));

        var stopCmd = mock(com.github.dockerjava.api.command.StopContainerCmd.class);
        var removeCmd = mock(com.github.dockerjava.api.command.RemoveContainerCmd.class);
        when(dockerClient.stopContainerCmd(anyString())).thenReturn(stopCmd);
        when(dockerClient.removeContainerCmd(anyString())).thenReturn(removeCmd);

        reconciler.reconcile();

        // 2개 제거
        verify(dockerClient, times(2)).stopContainerCmd(anyString());
        verify(dockerClient, times(2)).removeContainerCmd(anyString());
    }

    @Test
    @DisplayName("crashed 상태 컨테이너도 actual에 포함하지 않음 (exited는 제거 대상)")
    void reconcile_CountsOnlyRunningContainers() {
        // desired: 2, running: 1, exited: 1 → 1개 생성
        DesiredStateProperties.ServiceSpec spec = serviceSpec("demo-api", "ghcr.io/myorg/demo-api:latest", 2);
        properties.setEnabled(true);
        properties.setServices(List.of(spec));

        Container running = mockContainer("demo-api-1", "running");
        Container exited = mockContainer("demo-api-2", "exited");
        when(listContainersCmd.exec()).thenReturn(List.of(running, exited));

        reconciler.reconcile();

        verify(containerFactory, times(1)).create(eq(spec), anyInt());
    }

    @Test
    @DisplayName("nodeId가 있으면 해당 노드 클라이언트로 컨테이너 조회 및 제거")
    void reconcile_WithNodeId_UsesNodeClient() {
        // given
        String nodeId = "node-res";
        Node node = mock(Node.class);
        DockerClient nodeClient = mock(DockerClient.class);
        ListContainersCmd nodeListCmd = mock(ListContainersCmd.class);

        when(nodeRegistry.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeClientFactory.createClient(node)).thenReturn(nodeClient);
        when(nodeClient.listContainersCmd()).thenReturn(nodeListCmd);
        when(nodeListCmd.withShowAll(true)).thenReturn(nodeListCmd);

        // desired: 1, actual: 2 → 1개 제거
        Container c1 = mockContainer("engine-0", "running");
        Container c2 = mockContainer("engine-1", "running");
        when(nodeListCmd.exec()).thenReturn(List.of(c1, c2));

        var stopCmd = mock(com.github.dockerjava.api.command.StopContainerCmd.class);
        var removeCmd = mock(com.github.dockerjava.api.command.RemoveContainerCmd.class);
        when(nodeClient.stopContainerCmd(anyString())).thenReturn(stopCmd);
        when(nodeClient.removeContainerCmd(anyString())).thenReturn(removeCmd);

        DesiredStateProperties.ServiceSpec spec = serviceSpec("engine", "ghcr.io/myorg/engine:latest", 1);
        spec.setNodeId(nodeId);
        properties.setEnabled(true);
        properties.setServices(List.of(spec));

        // when
        reconciler.reconcile();

        // then: 로컬 dockerClient로 listContainersCmd 호출 안 함
        verify(dockerClient, never()).listContainersCmd();
        // 노드 클라이언트로 제거
        verify(nodeClient).stopContainerCmd(anyString());
        verify(nodeClient).removeContainerCmd(anyString());
    }

    @Test
    @DisplayName("nodeId가 없으면 로컬 dockerClient 사용")
    void reconcile_WithoutNodeId_UsesLocalClient() {
        DesiredStateProperties.ServiceSpec spec = serviceSpec("demo-api", "ghcr.io/myorg/demo-api:latest", 1);
        properties.setEnabled(true);
        properties.setServices(List.of(spec));

        Container c1 = mockContainer("demo-api-1", "running");
        when(listContainersCmd.exec()).thenReturn(List.of(c1));

        reconciler.reconcile();

        verify(dockerClient).listContainersCmd();
        verifyNoInteractions(nodeRegistry, nodeClientFactory);
    }

    private DesiredStateProperties.ServiceSpec serviceSpec(String name, String image, int replicas) {
        DesiredStateProperties.ServiceSpec spec = new DesiredStateProperties.ServiceSpec();
        spec.setName(name);
        spec.setImage(image);
        spec.setReplicas(replicas);
        spec.setContainerNamePrefix(name);
        return spec;
    }

    private Container mockContainer(String name, String state) {
        Container c = mock(Container.class);
        when(c.getId()).thenReturn(name + "-id");
        when(c.getNames()).thenReturn(new String[]{"/" + name});
        when(c.getState()).thenReturn(state);
        return c;
    }
}
