package com.lite_k8s.node;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContainerMigratorTest {

    @Mock private NodeRegistry nodeRegistry;
    @Mock private NodeDockerClientFactory clientFactory;
    @Mock private PlacementStrategy placementStrategy;
    @Mock private DockerClient failedNodeClient;
    @Mock private DockerClient targetNodeClient;
    @Mock private ListContainersCmd listContainersCmd;
    @Mock private InspectContainerCmd inspectCmd;
    @Mock private InspectContainerResponse inspectResponse;
    @Mock private CreateContainerCmd createContainerCmd;
    @Mock private CreateContainerResponse createContainerResponse;
    @Mock private StartContainerCmd startContainerCmd;

    private ContainerMigrator migrator;

    @BeforeEach
    void setUp() {
        migrator = new ContainerMigrator(nodeRegistry, clientFactory, placementStrategy);
    }

    @Test
    @DisplayName("장애 노드의 컨테이너를 healthy 노드로 이동")
    void onNodeFailure_MigratesContainersToHealthyNode() {
        // given
        Node failedNode = Node.builder().id("n1").name("s1").host("h1").port(2375)
                .status(NodeStatus.UNHEALTHY).build();
        Node targetNode = Node.builder().id("n2").name("s2").host("h2").port(2375)
                .status(NodeStatus.HEALTHY).build();

        NodeFailureEvent event = new NodeFailureEvent(failedNode);

        Container container = mock(Container.class);
        when(container.getId()).thenReturn("c1");
        when(container.getNames()).thenReturn(new String[]{"/web-app"});

        when(clientFactory.createClient(failedNode)).thenReturn(failedNodeClient);
        when(clientFactory.createClient(targetNode)).thenReturn(targetNodeClient);
        when(failedNodeClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));

        when(nodeRegistry.findHealthy()).thenReturn(List.of(targetNode));
        when(placementStrategy.selectNode(List.of(targetNode))).thenReturn(Optional.of(targetNode));

        // inspect on failed node
        var config = mock(com.github.dockerjava.api.model.ContainerConfig.class);
        when(config.getImage()).thenReturn("myapp:latest");
        when(config.getEnv()).thenReturn(new String[]{});
        when(config.getLabels()).thenReturn(java.util.Map.of());
        when(inspectResponse.getName()).thenReturn("/web-app");
        when(inspectResponse.getConfig()).thenReturn(config);
        when(inspectResponse.getHostConfig()).thenReturn(
                com.github.dockerjava.api.model.HostConfig.newHostConfig());
        when(inspectCmd.exec()).thenReturn(inspectResponse);
        when(failedNodeClient.inspectContainerCmd("c1")).thenReturn(inspectCmd);

        // create+start on target node
        when(createContainerResponse.getId()).thenReturn("new-c1");
        when(targetNodeClient.createContainerCmd(anyString())).thenReturn(createContainerCmd);
        when(createContainerCmd.withName(anyString())).thenReturn(createContainerCmd);
        when(createContainerCmd.withHostConfig(any())).thenReturn(createContainerCmd);
        when(createContainerCmd.withEnv(any(String[].class))).thenReturn(createContainerCmd);
        when(createContainerCmd.withLabels(any())).thenReturn(createContainerCmd);
        when(createContainerCmd.exec()).thenReturn(createContainerResponse);
        when(targetNodeClient.startContainerCmd("new-c1")).thenReturn(startContainerCmd);
        doNothing().when(startContainerCmd).exec();

        // when
        migrator.onNodeFailure(event);

        // then - target node에 컨테이너 생성됨
        verify(targetNodeClient).createContainerCmd("myapp:latest");
        verify(targetNodeClient).startContainerCmd("new-c1");
    }

    @Test
    @DisplayName("healthy 노드 없으면 마이그레이션 스킵")
    void onNodeFailure_WhenNoHealthyNodes_SkipsMigration() {
        Node failedNode = Node.builder().id("n1").name("s1").host("h1").port(2375)
                .status(NodeStatus.UNHEALTHY).build();
        NodeFailureEvent event = new NodeFailureEvent(failedNode);

        when(clientFactory.createClient(failedNode)).thenReturn(failedNodeClient);
        when(failedNodeClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(mock(Container.class)));
        when(nodeRegistry.findHealthy()).thenReturn(List.of());
        when(placementStrategy.selectNode(List.of())).thenReturn(Optional.empty());

        migrator.onNodeFailure(event);

        verifyNoInteractions(targetNodeClient);
    }
}
