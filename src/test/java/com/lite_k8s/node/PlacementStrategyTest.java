package com.lite_k8s.node;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlacementStrategyTest {

    // ── LeastUsed ─────────────────────────────────────────────────

    @Test
    @DisplayName("[LeastUsed] CPU+메모리 합산이 가장 낮은 노드 선택")
    void leastUsed_SelectsNodeWithLowestLoad() {
        LeastUsedStrategy strategy = new LeastUsedStrategy();

        Node low = node("n1", 20.0, 30.0);   // 합계 50
        Node mid = node("n2", 40.0, 50.0);   // 합계 90
        Node high = node("n3", 70.0, 80.0);  // 합계 150

        Optional<Node> selected = strategy.selectNode(List.of(high, mid, low));

        assertThat(selected).isPresent();
        assertThat(selected.get().getId()).isEqualTo("n1");
    }

    @Test
    @DisplayName("[LeastUsed] 빈 목록이면 empty 반환")
    void leastUsed_EmptyList_ReturnsEmpty() {
        LeastUsedStrategy strategy = new LeastUsedStrategy();
        assertThat(strategy.selectNode(List.of())).isEmpty();
    }

    @Test
    @DisplayName("[LeastUsed] 단일 노드면 그 노드 반환")
    void leastUsed_SingleNode_ReturnsThatNode() {
        LeastUsedStrategy strategy = new LeastUsedStrategy();
        Node single = node("n1", 50.0, 60.0);

        assertThat(strategy.selectNode(List.of(single))).isPresent();
        assertThat(strategy.selectNode(List.of(single)).get().getId()).isEqualTo("n1");
    }

    // ── RoundRobin ─────────────────────────────────────────────────

    @Test
    @DisplayName("[RoundRobin] 순서대로 순환 선택")
    void roundRobin_SelectsNodesInOrder() {
        RoundRobinStrategy strategy = new RoundRobinStrategy();

        Node n1 = node("n1", 80.0, 80.0);  // load 높아도 순서대로
        Node n2 = node("n2", 10.0, 10.0);
        Node n3 = node("n3", 50.0, 50.0);
        List<Node> nodes = List.of(n1, n2, n3);

        assertThat(strategy.selectNode(nodes).get().getId()).isEqualTo("n1");
        assertThat(strategy.selectNode(nodes).get().getId()).isEqualTo("n2");
        assertThat(strategy.selectNode(nodes).get().getId()).isEqualTo("n3");
        assertThat(strategy.selectNode(nodes).get().getId()).isEqualTo("n1"); // 순환
    }

    @Test
    @DisplayName("[RoundRobin] 빈 목록이면 empty 반환")
    void roundRobin_EmptyList_ReturnsEmpty() {
        RoundRobinStrategy strategy = new RoundRobinStrategy();
        assertThat(strategy.selectNode(List.of())).isEmpty();
    }

    private Node node(String id, double cpu, double memory) {
        return Node.builder()
                .id(id)
                .name(id)
                .host("host-" + id)
                .port(2375)
                .status(NodeStatus.HEALTHY)
                .cpuUsagePercent(cpu)
                .memoryUsagePercent(memory)
                .build();
    }
}
