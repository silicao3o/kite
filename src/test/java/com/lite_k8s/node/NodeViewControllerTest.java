package com.lite_k8s.node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodeViewControllerTest {

    @Mock private NodeRegistry nodeRegistry;
    @Mock private Model model;

    private NodeViewController controller;

    @BeforeEach
    void setUp() {
        controller = new NodeViewController(nodeRegistry);
    }

    @Test
    @DisplayName("GET /nodes - 노드 목록 페이지 반환")
    void nodes_ShouldReturnNodesViewWithNodeList() {
        // given
        List<Node> nodes = List.of(
                Node.builder().id("node-a").name("vm-a").host("192.168.1.10").port(2375)
                        .status(NodeStatus.HEALTHY).runningContainers(3).build()
        );
        when(nodeRegistry.findAll()).thenReturn(nodes);

        // when
        String viewName = controller.nodes(model);

        // then
        assertThat(viewName).isEqualTo("nodes");
        verify(model).addAttribute(eq("nodes"), eq(nodes));
    }

    @Test
    @DisplayName("노드가 없을 때 빈 목록 전달")
    void nodes_WhenNoNodesRegistered_ShouldPassEmptyList() {
        // given
        when(nodeRegistry.findAll()).thenReturn(List.of());

        // when
        String viewName = controller.nodes(model);

        // then
        assertThat(viewName).isEqualTo("nodes");
        verify(model).addAttribute(eq("nodes"), eq(List.of()));
    }
}
