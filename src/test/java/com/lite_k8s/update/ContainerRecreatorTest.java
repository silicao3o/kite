package com.lite_k8s.update;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.InOrder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContainerRecreatorTest {

    @Mock private DockerClient dockerClient;
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
        recreator = new ContainerRecreator(dockerClient);
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
