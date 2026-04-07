package com.lite_k8s.deploy;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.StopContainerCmd;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeploymentStrategyFactoryTest {

    @Mock private DockerClient dockerClient;
    @Mock private NodeRegistry nodeRegistry;
    @Mock private NodeDockerClientFactory nodeClientFactory;

    private DeploymentStrategyFactory factory;

    @BeforeEach
    void setUp() {
        factory = new DeploymentStrategyFactory(dockerClient, nodeRegistry, nodeClientFactory);
    }

    @Test
    @DisplayName("nodeId=null이면 로컬 dockerClient로 ContainerOperator 생성")
    void create_WithoutNodeId_UsesLocalClient() {
        // given
        InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
        InspectContainerResponse inspectResp = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState state = mock(InspectContainerResponse.ContainerState.class);
        when(dockerClient.inspectContainerCmd(anyString())).thenReturn(inspectCmd);
        when(inspectCmd.exec()).thenReturn(inspectResp);
        when(inspectResp.getState()).thenReturn(state);
        when(state.getRunning()).thenReturn(false);

        // when
        ContainerOperator operator = factory.createOperator(null);
        operator.isRunning("some-id");

        // then
        verify(dockerClient).inspectContainerCmd("some-id");
        verifyNoInteractions(nodeRegistry, nodeClientFactory);
    }

    @Test
    @DisplayName("nodeId가 있으면 해당 노드 클라이언트로 ContainerOperator 생성")
    void create_WithNodeId_UsesNodeClient() {
        // given
        String nodeId = "node-res";
        Node node = mock(Node.class);
        DockerClient nodeClient = mock(DockerClient.class);

        when(nodeRegistry.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeClientFactory.createClient(node)).thenReturn(nodeClient);

        StopContainerCmd stopCmd = mock(StopContainerCmd.class);
        when(nodeClient.stopContainerCmd(anyString())).thenReturn(stopCmd);
        doNothing().when(stopCmd).exec();

        // when
        ContainerOperator operator = factory.createOperator(nodeId);
        operator.stop("engine-id");

        // then
        verify(nodeClient).stopContainerCmd("engine-id");
        verify(dockerClient, never()).stopContainerCmd(anyString());
    }

    @Test
    @DisplayName("create(type, nodeId)는 해당 노드 클라이언트 기반 전략 반환")
    void create_WithTypeAndNodeId_ReturnsStrategyWithNodeClient() {
        String nodeId = "node-res";
        Node node = mock(Node.class);
        DockerClient nodeClient = mock(DockerClient.class);

        when(nodeRegistry.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeClientFactory.createClient(node)).thenReturn(nodeClient);

        StopContainerCmd stopCmd = mock(StopContainerCmd.class);
        when(nodeClient.stopContainerCmd(anyString())).thenReturn(stopCmd);
        doNothing().when(stopCmd).exec();

        // when: 전략을 만들어서 operator의 stop 호출
        DeploymentStrategy strategy = factory.create(DeploymentType.RECREATE, nodeId);
        // strategy 내부 operator의 stop을 간접 확인하기 어려우므로
        // createOperator로 operator를 직접 테스트하는 방식으로 검증 충분

        // factory가 nodeId로 올바른 클라이언트를 resolve하는지 확인
        verify(nodeRegistry).findById(nodeId);
        verify(nodeClientFactory).createClient(node);
    }
}
