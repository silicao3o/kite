package com.lite_k8s.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.lite_k8s.compose.ServiceDefinitionRepository;
import com.lite_k8s.compose.ServiceDeployer;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContainerRecreateServiceTest {

    @Mock private DockerClient dockerClient;
    @Mock private NodeRegistry nodeRegistry;
    @Mock private NodeDockerClientFactory nodeClientFactory;
    @Mock private ServiceDefinitionRepository serviceDefinitionRepository;
    @Mock private ServiceDeployer serviceDeployer;
    @Mock private InspectContainerCmd inspectCmd;
    @Mock private InspectContainerResponse inspectResponse;
    @Mock private ContainerConfig containerConfig;

    private ContainerRecreateService service;

    @BeforeEach
    void setUp() {
        service = new ContainerRecreateService(dockerClient, nodeRegistry, nodeClientFactory,
                new com.lite_k8s.service.OwnActionTracker(), serviceDefinitionRepository, serviceDeployer);
    }

    @Test
    @DisplayName("inspectContainer로 이미지명을 추출한다")
    void shouldExtractImageNameFromInspect() {
        when(dockerClient.inspectContainerCmd("abc123")).thenReturn(inspectCmd);
        when(inspectCmd.exec()).thenReturn(inspectResponse);
        when(inspectResponse.getConfig()).thenReturn(containerConfig);
        when(containerConfig.getImage()).thenReturn("ghcr.io/silicao3o/exercise-auth:latest");

        String imageName = service.getImageName("abc123");

        assertThat(imageName).isEqualTo("ghcr.io/silicao3o/exercise-auth:latest");
    }

    @Test
    @DisplayName("pullImage 성공 시 예외 없이 완료된다")
    @SuppressWarnings("unchecked")
    void shouldCompletePullImageSuccessfully() throws InterruptedException {
        PullImageCmd pullImageCmd = mock(PullImageCmd.class);
        ResultCallback.Adapter<PullResponseItem> callback = mock(ResultCallback.Adapter.class);
        when(dockerClient.pullImageCmd("ghcr.io/silicao3o/exercise-auth:latest")).thenReturn(pullImageCmd);
        when(pullImageCmd.withPlatform(anyString())).thenReturn(pullImageCmd);
        when(pullImageCmd.exec(any())).thenReturn(callback);
        when(callback.awaitCompletion(anyLong(), any())).thenReturn(true);

        assertThatCode(() -> service.pullImage(dockerClient, "ghcr.io/silicao3o/exercise-auth:latest"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("pullImage 실패 시 RuntimeException이 발생한다")
    void shouldThrowWhenPullImageFails() {
        PullImageCmd pullImageCmd = mock(PullImageCmd.class);
        when(dockerClient.pullImageCmd(anyString())).thenReturn(pullImageCmd);
        when(pullImageCmd.withPlatform(anyString())).thenReturn(pullImageCmd);
        when(pullImageCmd.exec(any())).thenThrow(new NotFoundException("이미지 없음"));

        assertThatThrownBy(() -> service.pullImage(dockerClient, "unknown:latest"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("inspect 응답에서 ContainerRecreateConfig를 빌드한다")
    void shouldBuildConfigFromInspect() {
        HostConfig hostConfig = HostConfig.newHostConfig();
        when(inspectResponse.getName()).thenReturn("/exercise-auth");
        when(inspectResponse.getConfig()).thenReturn(containerConfig);
        when(containerConfig.getImage()).thenReturn("ghcr.io/silicao3o/exercise-auth:latest");
        when(containerConfig.getEnv()).thenReturn(new String[]{"SPRING_PROFILES_ACTIVE=dev"});
        when(containerConfig.getLabels()).thenReturn(Map.of("self-healing.enabled", "true"));
        when(inspectResponse.getHostConfig()).thenReturn(hostConfig);

        ContainerRecreateConfig config = service.buildConfig(inspectResponse);

        assertThat(config.imageName()).isEqualTo("ghcr.io/silicao3o/exercise-auth:latest");
        assertThat(config.containerName()).isEqualTo("exercise-auth");
        assertThat(config.env()).containsExactly("SPRING_PROFILES_ACTIVE=dev");
        assertThat(config.labels()).containsEntry("self-healing.enabled", "true");
        assertThat(config.hostConfig()).isEqualTo(hostConfig);
    }

    @Test
    @DisplayName("pullAndRecreate: pull → stop → remove → create → start 순서로 실행한다")
    @SuppressWarnings("unchecked")
    void shouldExecutePullStopRemoveCreateStartInOrder() throws InterruptedException {
        setupInspect(dockerClient, "abc123", "my-image:latest");

        PullImageCmd pullCmd = mock(PullImageCmd.class);
        ResultCallback.Adapter<PullResponseItem> callback = mock(ResultCallback.Adapter.class);
        when(dockerClient.pullImageCmd("my-image:latest")).thenReturn(pullCmd);
        when(pullCmd.withPlatform(anyString())).thenReturn(pullCmd);
        when(pullCmd.exec(any())).thenReturn(callback);
        when(callback.awaitCompletion(anyLong(), any())).thenReturn(true);

        StopContainerCmd stopCmd = mock(StopContainerCmd.class);
        when(dockerClient.stopContainerCmd("abc123")).thenReturn(stopCmd);

        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        when(dockerClient.removeContainerCmd("abc123")).thenReturn(removeCmd);

        CreateContainerCmd createCmd = mockCreateCmd(dockerClient, "my-image:latest", "new-id");

        StartContainerCmd startCmd = mock(StartContainerCmd.class);
        when(dockerClient.startContainerCmd("new-id")).thenReturn(startCmd);

        service.pullAndRecreate("abc123", null);

        InOrder order = inOrder(dockerClient);
        order.verify(dockerClient).pullImageCmd("my-image:latest");
        order.verify(dockerClient).stopContainerCmd("abc123");
        order.verify(dockerClient).removeContainerCmd("abc123");
        order.verify(dockerClient).createContainerCmd("my-image:latest");
        order.verify(dockerClient).startContainerCmd("new-id");
    }

    @Test
    @DisplayName("nodeId가 있으면 해당 노드의 DockerClient를 사용한다")
    @SuppressWarnings("unchecked")
    void shouldUseNodeClientWhenNodeIdProvided() throws InterruptedException {
        Node node = Node.builder().id("node-1").name("vm-b").host("192.168.1.20").port(2375).build();
        DockerClient nodeClient = mock(DockerClient.class);
        when(nodeRegistry.findById("node-1")).thenReturn(Optional.of(node));
        when(nodeClientFactory.createClient(node)).thenReturn(nodeClient);

        setupInspect(nodeClient, "abc123", "my-image:latest");

        PullImageCmd pullCmd = mock(PullImageCmd.class);
        ResultCallback.Adapter<PullResponseItem> callback = mock(ResultCallback.Adapter.class);
        when(nodeClient.pullImageCmd("my-image:latest")).thenReturn(pullCmd);
        when(pullCmd.withPlatform(anyString())).thenReturn(pullCmd);
        when(pullCmd.exec(any())).thenReturn(callback);
        when(callback.awaitCompletion(anyLong(), any())).thenReturn(true);

        when(nodeClient.stopContainerCmd("abc123")).thenReturn(mock(StopContainerCmd.class));
        when(nodeClient.removeContainerCmd("abc123")).thenReturn(mock(RemoveContainerCmd.class));
        mockCreateCmd(nodeClient, "my-image:latest", "new-id");
        when(nodeClient.startContainerCmd("new-id")).thenReturn(mock(StartContainerCmd.class));

        service.pullAndRecreate("abc123", "node-1");

        verify(nodeClient).inspectContainerCmd("abc123");
        verify(dockerClient, never()).inspectContainerCmd("abc123");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void setupInspect(DockerClient client, String containerId, String image) {
        InspectContainerCmd iCmd = mock(InspectContainerCmd.class);
        InspectContainerResponse iResp = mock(InspectContainerResponse.class);
        ContainerConfig cfg = mock(ContainerConfig.class);
        when(client.inspectContainerCmd(containerId)).thenReturn(iCmd);
        when(iCmd.exec()).thenReturn(iResp);
        when(iResp.getName()).thenReturn("/" + containerId);
        when(iResp.getConfig()).thenReturn(cfg);
        when(cfg.getImage()).thenReturn(image);
        when(cfg.getEnv()).thenReturn(new String[]{});
        when(cfg.getLabels()).thenReturn(Map.of());
        when(iResp.getHostConfig()).thenReturn(HostConfig.newHostConfig());
    }

    private CreateContainerCmd mockCreateCmd(DockerClient client, String image, String newId) {
        CreateContainerCmd createCmd = mock(CreateContainerCmd.class);
        CreateContainerResponse createResp = mock(CreateContainerResponse.class);
        when(client.createContainerCmd(image)).thenReturn(createCmd);
        when(createCmd.withName(anyString())).thenReturn(createCmd);
        when(createCmd.withEnv(any(String[].class))).thenReturn(createCmd);
        when(createCmd.withHostConfig(any())).thenReturn(createCmd);
        when(createCmd.withLabels(any())).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(createResp);
        when(createResp.getId()).thenReturn(newId);
        return createCmd;
    }
}
