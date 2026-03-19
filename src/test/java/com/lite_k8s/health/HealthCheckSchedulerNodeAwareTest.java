package com.lite_k8s.health;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.NetworkSettings;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import com.lite_k8s.node.NodeStatus;
import com.lite_k8s.service.DockerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HealthCheckSchedulerNodeAwareTest {

    @Mock private ProbeRunner probeRunner;
    @Mock private DockerClient localClient;
    @Mock private DockerClient remoteClient;
    @Mock private DockerService dockerService;
    @Mock private NodeRegistry nodeRegistry;
    @Mock private NodeDockerClientFactory nodeClientFactory;
    @Mock private ListContainersCmd localListCmd;
    @Mock private ListContainersCmd remoteListCmd;
    @Mock private InspectContainerCmd localInspectCmd;
    @Mock private InspectContainerCmd remoteInspectCmd;
    @Mock private InspectContainerResponse localInspectResp;
    @Mock private InspectContainerResponse remoteInspectResp;
    @Mock private NetworkSettings localNetworkSettings;
    @Mock private NetworkSettings remoteNetworkSettings;

    private HealthCheckProperties properties;
    private HealthCheckStateTracker stateTracker;
    private HealthCheckScheduler scheduler;

    private Node remoteNode;

    @BeforeEach
    void setUp() {
        properties = new HealthCheckProperties();
        properties.setEnabled(true);
        stateTracker = new HealthCheckStateTracker();

        remoteNode = Node.builder()
                .id("node-b").name("vm-b").host("192.168.1.20").port(2375)
                .status(NodeStatus.HEALTHY).build();

        scheduler = new HealthCheckScheduler(
                properties, probeRunner, localClient, dockerService, stateTracker,
                nodeRegistry, nodeClientFactory
        );
    }

    @Test
    @DisplayName("노드 등록 시 원격 노드의 컨테이너에도 probe 실행")
    void runProbes_WhenNodesRegistered_ShouldProbeRemoteContainers() {
        // given
        when(nodeRegistry.findAll()).thenReturn(List.of(remoteNode));
        when(nodeClientFactory.createClient(remoteNode)).thenReturn(remoteClient);

        Container remoteContainer = mock(Container.class);
        when(remoteContainer.getId()).thenReturn("remote-c1");
        when(remoteContainer.getNames()).thenReturn(new String[]{"/my-app"});

        when(remoteClient.listContainersCmd()).thenReturn(remoteListCmd);
        when(remoteListCmd.withShowAll(false)).thenReturn(remoteListCmd);
        when(remoteListCmd.exec()).thenReturn(List.of(remoteContainer));

        when(remoteClient.inspectContainerCmd(anyString())).thenReturn(remoteInspectCmd);
        when(remoteInspectCmd.exec()).thenReturn(remoteInspectResp);
        when(remoteInspectResp.getNetworkSettings()).thenReturn(remoteNetworkSettings);
        when(remoteNetworkSettings.getIpAddress()).thenReturn("192.168.1.20");

        ProbeConfig probe = new ProbeConfig();
        probe.setType(ProbeType.TCP);
        probe.setPort(8080);
        probe.setInitialDelaySeconds(0);
        probe.setFailureThreshold(3);

        HealthCheckProperties.ContainerProbeConfig config = new HealthCheckProperties.ContainerProbeConfig();
        config.setContainerPattern("my-app");
        config.setLiveness(probe);
        properties.setProbes(List.of(config));

        when(probeRunner.run(anyString(), anyString(), any())).thenReturn(ProbeResult.success(10));

        // when
        scheduler.runProbes();

        // then: 원격 노드의 컨테이너에 probe 실행
        verify(remoteClient).listContainersCmd();
        verify(probeRunner, atLeastOnce()).run(anyString(), eq("remote-c1"), any());

        // 로컬 클라이언트는 컨테이너 목록 조회에 사용되지 않음
        verify(localClient, never()).listContainersCmd();
    }

    @Test
    @DisplayName("노드 미등록 시 로컬 클라이언트 폴백")
    void runProbes_WhenNoNodesRegistered_ShouldUseLocalClient() {
        // given
        when(nodeRegistry.findAll()).thenReturn(List.of());

        when(localClient.listContainersCmd()).thenReturn(localListCmd);
        when(localListCmd.withShowAll(false)).thenReturn(localListCmd);
        when(localListCmd.exec()).thenReturn(List.of());

        // when
        scheduler.runProbes();

        // then: 로컬 클라이언트 사용
        verify(localClient).listContainersCmd();
        verify(remoteClient, never()).listContainersCmd();
    }
}
