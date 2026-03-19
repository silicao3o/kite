package com.lite_k8s.node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodeManagementControllerTest {

    @Mock private NodeRegistry nodeRegistry;

    private NodeManagementController controller;

    @BeforeEach
    void setUp() {
        controller = new NodeManagementController(nodeRegistry);
    }

    @Test
    @DisplayName("POST /api/nodes - 노드 추가")
    void addNode_ShouldRegisterNodeInRegistry() {
        // given
        NodeManagementController.AddNodeRequest request = new NodeManagementController.AddNodeRequest();
        request.setName("vm-b");
        request.setHost("192.168.1.20");
        request.setPort(2375);

        // when
        NodeManagementController.NodeResponse response = controller.addNode(request);

        // then
        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(nodeRegistry).register(nodeCaptor.capture());
        Node registered = nodeCaptor.getValue();
        assertThat(registered.getName()).isEqualTo("vm-b");
        assertThat(registered.getHost()).isEqualTo("192.168.1.20");
        assertThat(registered.getPort()).isEqualTo(2375);
        assertThat(registered.getId()).isNotNull();
        assertThat(response.getId()).isEqualTo(registered.getId());
    }

    @Test
    @DisplayName("DELETE /api/nodes/{id} - 노드 제거")
    void removeNode_ShouldUnregisterFromRegistry() {
        // when
        controller.removeNode("node-b");

        // then
        verify(nodeRegistry).unregister("node-b");
    }

    @Test
    @DisplayName("GET /api/nodes - 노드 목록 조회")
    void listNodes_ShouldReturnAllNodes() {
        // given
        Node nodeA = Node.builder().id("node-a").name("vm-a").host("192.168.1.10").port(2375)
                .status(NodeStatus.HEALTHY).build();
        Node nodeB = Node.builder().id("node-b").name("vm-b").host("192.168.1.20").port(2375)
                .status(NodeStatus.UNHEALTHY).build();
        when(nodeRegistry.findAll()).thenReturn(List.of(nodeA, nodeB));

        // when
        List<NodeManagementController.NodeResponse> result = controller.listNodes();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("node-a");
        assertThat(result.get(0).getStatus()).isEqualTo("HEALTHY");
        assertThat(result.get(1).getId()).isEqualTo("node-b");
        assertThat(result.get(1).getStatus()).isEqualTo("UNHEALTHY");
    }
}
