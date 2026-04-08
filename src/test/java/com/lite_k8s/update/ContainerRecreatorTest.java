package com.lite_k8s.update;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.InOrder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContainerRecreatorTest {

    @Mock private DockerClient dockerClient;
    @Mock private NodeRegistry nodeRegistry;
    @Mock private NodeDockerClientFactory nodeClientFactory;
    @Mock private InspectContainerCmd inspectCmd;
    @Mock private InspectContainerResponse inspectResponse;
    @Mock private InspectContainerResponse.ContainerState state;
    @Mock private StopContainerCmd stopCmd;
    @Mock private RemoveContainerCmd removeCmd;
    @Mock private CreateContainerCmd createCmd;
    @Mock private CreateContainerResponse createResponse;
    @Mock private StartContainerCmd startCmd;

    private ContainerRecreator recreator;

    @BeforeEach
    void setUp() {
        recreator = new ContainerRecreator(dockerClient, nodeRegistry, nodeClientFactory, new com.lite_k8s.service.OwnActionTracker());
    }

    @Test
    @DisplayName("정상 재생성: stop → remove → create → start 순서로 호출")
    void recreate_Success_CallsInCorrectOrder() {
        // given
        setupInspect("old-container", "ghcr.io/myorg/myapp:latest");
        setupDockerCommands("new-container-id");

        // when
        boolean result = recreator.recreate("old-container", "ghcr.io/myorg/myapp", "sha256:new");

        // then
        assertThat(result).isTrue();
        InOrder order = inOrder(dockerClient);
        order.verify(dockerClient).stopContainerCmd("old-container");
        order.verify(dockerClient).removeContainerCmd("old-container");
        order.verify(dockerClient).createContainerCmd(anyString());
        order.verify(dockerClient).startContainerCmd("new-container-id");
    }

    @Test
    @DisplayName("stop 실패 시 false 반환 및 롤백")
    void recreate_WhenStopFails_ReturnsFalse() {
        // given
        setupInspect("old-container", "ghcr.io/myorg/myapp:latest");
        when(dockerClient.stopContainerCmd(anyString())).thenReturn(stopCmd);
        doThrow(new RuntimeException("stop failed")).when(stopCmd).exec();

        // when
        boolean result = recreator.recreate("old-container", "ghcr.io/myorg/myapp", "sha256:new");

        // then
        assertThat(result).isFalse();
        verify(dockerClient, never()).removeContainerCmd(anyString());
        verify(dockerClient, never()).createContainerCmd(anyString());
    }

    @Test
    @DisplayName("start 실패 시 false 반환 (롤백 시도)")
    void recreate_WhenStartFails_ReturnsFalse() {
        // given
        setupInspect("old-container", "ghcr.io/myorg/myapp:latest");
        setupDockerCommands("new-container-id");
        doThrow(new RuntimeException("start failed")).when(startCmd).exec();

        // when
        boolean result = recreator.recreate("old-container", "ghcr.io/myorg/myapp", "sha256:new");

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("nodeId가 있으면 해당 노드 클라이언트로 재생성")
    void recreate_WithNodeId_UsesNodeClient() {
        // given
        String nodeId = "node-res";
        Node node = mock(Node.class);
        DockerClient nodeClient = mock(DockerClient.class);

        when(nodeRegistry.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeClientFactory.createClient(node)).thenReturn(nodeClient);

        setupInspectOn(nodeClient, "old-container", "ghcr.io/myorg/engine:latest");
        setupDockerCommandsOn(nodeClient, "new-container-id");

        // when
        boolean result = recreator.recreate("old-container", "ghcr.io/myorg/engine", "sha256:new", nodeId);

        // then
        assertThat(result).isTrue();
        // 로컬 dockerClient 사용 안 함
        verify(dockerClient, never()).inspectContainerCmd(anyString());
        verify(dockerClient, never()).stopContainerCmd(anyString());
        // 노드 클라이언트 사용
        verify(nodeClient).stopContainerCmd("old-container");
        verify(nodeClient).startContainerCmd("new-container-id");
    }

    @Test
    @DisplayName("nodeId가 null이면 로컬 dockerClient로 재생성")
    void recreate_WithoutNodeId_UsesLocalClient() {
        // given
        setupInspect("old-container", "ghcr.io/myorg/myapp:latest");
        setupDockerCommands("new-container-id");

        // when
        boolean result = recreator.recreate("old-container", "ghcr.io/myorg/myapp", "sha256:new", null);

        // then
        assertThat(result).isTrue();
        verify(dockerClient).stopContainerCmd("old-container");
        verifyNoInteractions(nodeRegistry, nodeClientFactory);
    }

    private void setupInspect(String containerId, String image) {
        ContainerConfig config = mock(ContainerConfig.class);
        when(config.getImage()).thenReturn(image);
        when(config.getEnv()).thenReturn(new String[]{});
        when(config.getLabels()).thenReturn(java.util.Map.of());

        when(inspectResponse.getName()).thenReturn("/" + containerId);
        when(inspectResponse.getConfig()).thenReturn(config);
        when(inspectResponse.getHostConfig()).thenReturn(HostConfig.newHostConfig());

        when(dockerClient.inspectContainerCmd(containerId)).thenReturn(inspectCmd);
        when(inspectCmd.exec()).thenReturn(inspectResponse);
    }

    private void setupInspectOn(DockerClient client, String containerId, String image) {
        InspectContainerCmd cmd = mock(InspectContainerCmd.class);
        InspectContainerResponse resp = mock(InspectContainerResponse.class);
        ContainerConfig config = mock(ContainerConfig.class);
        when(config.getImage()).thenReturn(image);
        when(config.getEnv()).thenReturn(new String[]{});
        when(config.getLabels()).thenReturn(java.util.Map.of());
        when(resp.getName()).thenReturn("/" + containerId);
        when(resp.getConfig()).thenReturn(config);
        when(resp.getHostConfig()).thenReturn(HostConfig.newHostConfig());
        when(client.inspectContainerCmd(containerId)).thenReturn(cmd);
        when(cmd.exec()).thenReturn(resp);
    }

    private void setupDockerCommandsOn(DockerClient client, String newContainerId) {
        StopContainerCmd stop = mock(StopContainerCmd.class);
        RemoveContainerCmd remove = mock(RemoveContainerCmd.class);
        CreateContainerCmd create = mock(CreateContainerCmd.class);
        CreateContainerResponse resp = mock(CreateContainerResponse.class);
        StartContainerCmd start = mock(StartContainerCmd.class);

        when(client.stopContainerCmd(anyString())).thenReturn(stop);
        doNothing().when(stop).exec();
        when(client.removeContainerCmd(anyString())).thenReturn(remove);
        doNothing().when(remove).exec();
        when(resp.getId()).thenReturn(newContainerId);
        when(client.createContainerCmd(anyString())).thenReturn(create);
        when(create.withName(anyString())).thenReturn(create);
        when(create.withHostConfig(any())).thenReturn(create);
        when(create.withEnv(any(String[].class))).thenReturn(create);
        when(create.withLabels(any())).thenReturn(create);
        when(create.exec()).thenReturn(resp);
        when(client.startContainerCmd(newContainerId)).thenReturn(start);
        doNothing().when(start).exec();
    }

    private void setupDockerCommands(String newContainerId) {
        when(dockerClient.stopContainerCmd(anyString())).thenReturn(stopCmd);
        doNothing().when(stopCmd).exec();

        when(dockerClient.removeContainerCmd(anyString())).thenReturn(removeCmd);
        doNothing().when(removeCmd).exec();

        when(createResponse.getId()).thenReturn(newContainerId);
        when(dockerClient.createContainerCmd(anyString())).thenReturn(createCmd);
        when(createCmd.withName(anyString())).thenReturn(createCmd);
        when(createCmd.withHostConfig(any())).thenReturn(createCmd);
        when(createCmd.withEnv(any(String[].class))).thenReturn(createCmd);
        when(createCmd.withLabels(any())).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(createResponse);

        when(dockerClient.startContainerCmd(newContainerId)).thenReturn(startCmd);
        doNothing().when(startCmd).exec();
    }
}
