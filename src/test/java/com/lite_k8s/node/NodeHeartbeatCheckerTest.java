package com.lite_k8s.node;

import com.lite_k8s.service.MetricsCollector;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InfoCmd;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Info;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NodeHeartbeatCheckerTest {

    @Mock private NodeRegistry nodeRegistry;
    @Mock private NodeDockerClientFactory clientFactory;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private MetricsCollector metricsCollector;
    @Mock private DockerClient nodeDockerClient;
    @Mock private ListContainersCmd listContainersCmd;
    @Mock private InfoCmd infoCmd;
    @Mock private Info dockerInfo;

    private NodeHeartbeatChecker checker;

    @BeforeEach
    void setUp() {
        checker = new NodeHeartbeatChecker(nodeRegistry, clientFactory, eventPublisher, metricsCollector);
        when(nodeDockerClient.infoCmd()).thenReturn(infoCmd);
        when(infoCmd.exec()).thenReturn(dockerInfo);
        when(dockerInfo.getMemTotal()).thenReturn(8L * 1024 * 1024 * 1024); // 8GB
    }

    @Test
    @DisplayName("응답하는 노드는 HEALTHY로 마크")
    void checkHeartbeats_WhenNodeResponds_MarksHealthy() {
        Node node = Node.builder().id("n1").name("s1").host("192.168.1.10").port(2375)
                .status(NodeStatus.UNKNOWN).build();
        when(nodeRegistry.findAll()).thenReturn(List.of(node));
        when(clientFactory.createClient(node)).thenReturn(nodeDockerClient);
        when(nodeDockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of());

        checker.checkHeartbeats();

        verify(nodeRegistry).updateMetrics(eq("n1"), eq(0.0), eq(0.0), eq(0), eq(0L), anyLong());
        verify(nodeRegistry).updateStatus("n1", NodeStatus.HEALTHY);
        verify(eventPublisher, never()).publishEvent(any(NodeFailureEvent.class));
    }

    @Test
    @DisplayName("응답 없는 노드는 UNHEALTHY로 마크 + 이벤트 발행")
    void checkHeartbeats_WhenNodeFails_MarksUnhealthyAndPublishesEvent() {
        Node node = Node.builder().id("n1").name("s1").host("192.168.1.10").port(2375)
                .status(NodeStatus.HEALTHY).build();
        when(nodeRegistry.findAll()).thenReturn(List.of(node));
        when(clientFactory.createClient(node)).thenReturn(nodeDockerClient);
        when(nodeDockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenThrow(new RuntimeException("connection refused"));

        checker.checkHeartbeats();

        verify(nodeRegistry).updateStatus("n1", NodeStatus.UNHEALTHY);
        ArgumentCaptor<NodeFailureEvent> captor = ArgumentCaptor.forClass(NodeFailureEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getFailedNode().getId()).isEqualTo("n1");
    }

    @Test
    @DisplayName("이미 UNHEALTHY인 노드는 이벤트 중복 발행 안 함")
    void checkHeartbeats_WhenAlreadyUnhealthy_DoesNotRepublishEvent() {
        Node node = Node.builder().id("n1").name("s1").host("192.168.1.10").port(2375)
                .status(NodeStatus.UNHEALTHY).build();
        when(nodeRegistry.findAll()).thenReturn(List.of(node));
        when(clientFactory.createClient(node)).thenReturn(nodeDockerClient);
        when(nodeDockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenThrow(new RuntimeException("connection refused"));

        checker.checkHeartbeats();

        verify(nodeRegistry).updateStatus("n1", NodeStatus.UNHEALTHY);
        verify(eventPublisher, never()).publishEvent(any()); // 이미 UNHEALTHY → 이벤트 중복 없음
    }

    @Test
    @DisplayName("노드 없으면 아무것도 안 함")
    void checkHeartbeats_WhenNoNodes_DoesNothing() {
        when(nodeRegistry.findAll()).thenReturn(List.of());

        checker.checkHeartbeats();

        verifyNoInteractions(clientFactory, eventPublisher);
    }
}
