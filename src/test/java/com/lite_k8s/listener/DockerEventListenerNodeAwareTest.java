package com.lite_k8s.listener;

import com.lite_k8s.analyzer.ExitCodeAnalyzer;
import com.lite_k8s.config.MonitorProperties;
import com.lite_k8s.incident.IncidentReportService;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import com.lite_k8s.node.NodeStatus;
import com.lite_k8s.service.AlertDeduplicationService;
import com.lite_k8s.service.ContainerFilterService;
import com.lite_k8s.service.DockerService;
import com.lite_k8s.service.EmailNotificationService;
import com.lite_k8s.service.SelfHealingService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.EventsCmd;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DockerEventListenerNodeAwareTest {

    @Mock private DockerClient localDockerClient;
    @Mock private DockerClient nodeADockerClient;
    @Mock private DockerClient nodeBDockerClient;
    @Mock private DockerService dockerService;
    @Mock private ExitCodeAnalyzer exitCodeAnalyzer;
    @Mock private EmailNotificationService notificationService;
    @Mock private ContainerFilterService containerFilterService;
    @Mock private AlertDeduplicationService deduplicationService;
    @Mock private SelfHealingService selfHealingService;
    @Mock private IncidentReportService incidentReportService;
    @Mock private NodeRegistry nodeRegistry;
    @Mock private NodeDockerClientFactory nodeClientFactory;
    @Mock private EventsCmd nodeAEventsCmd;
    @Mock private EventsCmd nodeBEventsCmd;

    private MonitorProperties monitorProperties;
    private DockerEventListener listener;

    private Node nodeA;
    private Node nodeB;

    @BeforeEach
    void setUp() {
        monitorProperties = new MonitorProperties();

        nodeA = Node.builder().id("node-a").name("vm-a").host("192.168.1.10").port(2375)
                .status(NodeStatus.HEALTHY).build();
        nodeB = Node.builder().id("node-b").name("vm-b").host("192.168.1.20").port(2375)
                .status(NodeStatus.HEALTHY).build();

        listener = new DockerEventListener(
                localDockerClient, dockerService, exitCodeAnalyzer, notificationService,
                monitorProperties, containerFilterService, deduplicationService,
                selfHealingService, incidentReportService,
                nodeRegistry, nodeClientFactory
        );
    }

    @Test
    @DisplayName("nodes.enabled=true 시 로컬 + 노드별 이벤트 스트림 모두 생성")
    void startListening_WhenNodesRegistered_ShouldCreatePerNodeListeners() {
        // given
        java.util.List<Node> nodes = java.util.List.of(nodeA, nodeB);
        when(nodeRegistry.findAll()).thenReturn(nodes);
        when(nodeClientFactory.createClient(nodeA)).thenReturn(nodeADockerClient);
        when(nodeClientFactory.createClient(nodeB)).thenReturn(nodeBDockerClient);

        // 로컬 클라이언트도 항상 이벤트 스트림 생성
        EventsCmd localEventsCmd = mock(EventsCmd.class);
        when(localDockerClient.eventsCmd()).thenReturn(localEventsCmd);
        when(localEventsCmd.withEventTypeFilter(any(com.github.dockerjava.api.model.EventType.class))).thenReturn(localEventsCmd);
        when(localEventsCmd.exec(any())).thenReturn(null);

        when(nodeADockerClient.eventsCmd()).thenReturn(nodeAEventsCmd);
        when(nodeBDockerClient.eventsCmd()).thenReturn(nodeBEventsCmd);
        when(nodeAEventsCmd.withEventTypeFilter(any(com.github.dockerjava.api.model.EventType.class))).thenReturn(nodeAEventsCmd);
        when(nodeBEventsCmd.withEventTypeFilter(any(com.github.dockerjava.api.model.EventType.class))).thenReturn(nodeBEventsCmd);
        when(nodeAEventsCmd.exec(any())).thenReturn(null);
        when(nodeBEventsCmd.exec(any())).thenReturn(null);

        // when
        listener.startListening();

        // then: 로컬 + 각 노드의 클라이언트로 이벤트 스트림 생성
        verify(localDockerClient).eventsCmd();
        verify(nodeADockerClient).eventsCmd();
        verify(nodeBDockerClient).eventsCmd();
    }

    @Test
    @DisplayName("노드가 없으면 로컬 클라이언트로 폴백")
    void startListening_WhenNoNodesRegistered_ShouldFallbackToLocalClient() {
        // given
        when(nodeRegistry.findAll()).thenReturn(java.util.List.of());

        EventsCmd localEventsCmd = mock(EventsCmd.class);
        when(localDockerClient.eventsCmd()).thenReturn(localEventsCmd);
        when(localEventsCmd.withEventTypeFilter(any(com.github.dockerjava.api.model.EventType.class))).thenReturn(localEventsCmd);
        when(localEventsCmd.exec(any())).thenReturn(null);

        // when
        listener.startListening();

        // then: 로컬 클라이언트 사용
        verify(localDockerClient).eventsCmd();
        verify(nodeClientFactory, never()).createClient(any());
    }
}
