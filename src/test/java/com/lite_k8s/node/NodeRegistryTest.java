package com.lite_k8s.node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NodeRegistryTest {

    @Mock
    private NodeJpaRepository jpa;

    private NodeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NodeRegistry(jpa);
    }

    @Test
    @DisplayName("노드 등록 후 조회 가능")
    void register_ThenFindById_ReturnsNode() {
        Node node = Node.builder().id("n1").name("server-1").host("192.168.1.10").port(2375).build();
        when(jpa.save(node)).thenReturn(node);
        when(jpa.findById("n1")).thenReturn(Optional.of(node));

        registry.register(node);

        Optional<Node> found = registry.findById("n1");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("server-1");
    }

    @Test
    @DisplayName("등록되지 않은 ID 조회 시 empty")
    void findById_WhenNotRegistered_ReturnsEmpty() {
        when(jpa.findById("nonexistent")).thenReturn(Optional.empty());

        assertThat(registry.findById("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("등록 해제 후 조회 불가")
    void unregister_ThenFindById_ReturnsEmpty() {
        Node node = Node.builder().id("n1").name("server-1").host("192.168.1.10").port(2375).build();
        when(jpa.save(node)).thenReturn(node);
        registry.register(node);

        registry.unregister("n1");

        when(jpa.findById("n1")).thenReturn(Optional.empty());
        assertThat(registry.findById("n1")).isEmpty();
        verify(jpa).deleteById("n1");
    }

    @Test
    @DisplayName("전체 노드 목록 조회")
    void findAll_ReturnsAllRegisteredNodes() {
        Node n1 = Node.builder().id("n1").name("server-1").host("h1").port(2375).build();
        Node n2 = Node.builder().id("n2").name("server-2").host("h2").port(2375).build();
        when(jpa.findAll()).thenReturn(List.of(n1, n2));

        assertThat(registry.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("HEALTHY 노드만 필터링")
    void findHealthy_ReturnsOnlyHealthyNodes() {
        Node healthy = Node.builder().id("n1").name("s1").host("h1").port(2375)
                .status(NodeStatus.HEALTHY).build();
        Node unhealthy = Node.builder().id("n2").name("s2").host("h2").port(2375)
                .status(NodeStatus.UNHEALTHY).build();
        when(jpa.findAll()).thenReturn(List.of(healthy, unhealthy));

        assertThat(registry.findHealthy()).hasSize(1);
        assertThat(registry.findHealthy().get(0).getId()).isEqualTo("n1");
    }

    @Test
    @DisplayName("이름으로 노드 조회 — 존재하는 경우")
    void findByName_WhenExists_ReturnsNode() {
        Node node = Node.builder().id("n1").name("res").host("192.168.1.10").port(2375).build();
        when(jpa.findByName("res")).thenReturn(Optional.of(node));

        Optional<Node> found = registry.findByName("res");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo("n1");
    }

    @Test
    @DisplayName("이름으로 노드 조회 — 존재하지 않는 경우 empty")
    void findByName_WhenNotExists_ReturnsEmpty() {
        when(jpa.findByName("unknown")).thenReturn(Optional.empty());

        assertThat(registry.findByName("unknown")).isEmpty();
    }

    @Test
    @DisplayName("노드 상태 업데이트")
    void updateStatus_ChangesNodeStatus() {
        Node node = Node.builder().id("n1").name("s1").host("h1").port(2375)
                .status(NodeStatus.UNKNOWN).build();
        when(jpa.save(node)).thenReturn(node);
        when(jpa.findById("n1")).thenReturn(Optional.of(node));

        registry.register(node);
        registry.updateStatus("n1", NodeStatus.HEALTHY);

        assertThat(registry.findById("n1").get().getStatus()).isEqualTo(NodeStatus.HEALTHY);
    }
}
