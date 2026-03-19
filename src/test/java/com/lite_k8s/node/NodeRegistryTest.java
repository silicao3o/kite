package com.lite_k8s.node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class NodeRegistryTest {

    private NodeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NodeRegistry();
    }

    @Test
    @DisplayName("노드 등록 후 조회 가능")
    void register_ThenFindById_ReturnsNode() {
        Node node = Node.builder().id("n1").name("server-1").host("192.168.1.10").port(2375).build();

        registry.register(node);

        Optional<Node> found = registry.findById("n1");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("server-1");
    }

    @Test
    @DisplayName("등록되지 않은 ID 조회 시 empty")
    void findById_WhenNotRegistered_ReturnsEmpty() {
        assertThat(registry.findById("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("등록 해제 후 조회 불가")
    void unregister_ThenFindById_ReturnsEmpty() {
        Node node = Node.builder().id("n1").name("server-1").host("192.168.1.10").port(2375).build();
        registry.register(node);

        registry.unregister("n1");

        assertThat(registry.findById("n1")).isEmpty();
    }

    @Test
    @DisplayName("전체 노드 목록 조회")
    void findAll_ReturnsAllRegisteredNodes() {
        registry.register(Node.builder().id("n1").name("server-1").host("h1").port(2375).build());
        registry.register(Node.builder().id("n2").name("server-2").host("h2").port(2375).build());

        assertThat(registry.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("HEALTHY 노드만 필터링")
    void findHealthy_ReturnsOnlyHealthyNodes() {
        Node healthy = Node.builder().id("n1").name("s1").host("h1").port(2375)
                .status(NodeStatus.HEALTHY).build();
        Node unhealthy = Node.builder().id("n2").name("s2").host("h2").port(2375)
                .status(NodeStatus.UNHEALTHY).build();

        registry.register(healthy);
        registry.register(unhealthy);

        assertThat(registry.findHealthy()).hasSize(1);
        assertThat(registry.findHealthy().get(0).getId()).isEqualTo("n1");
    }

    @Test
    @DisplayName("노드 상태 업데이트")
    void updateStatus_ChangesNodeStatus() {
        Node node = Node.builder().id("n1").name("s1").host("h1").port(2375)
                .status(NodeStatus.UNKNOWN).build();
        registry.register(node);

        registry.updateStatus("n1", NodeStatus.HEALTHY);

        assertThat(registry.findById("n1").get().getStatus()).isEqualTo(NodeStatus.HEALTHY);
    }
}
