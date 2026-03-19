package com.lite_k8s.node;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 순서대로 순환하며 노드 선택
 */
public class RoundRobinStrategy implements PlacementStrategy {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public Optional<Node> selectNode(List<Node> healthyNodes) {
        if (healthyNodes.isEmpty()) return Optional.empty();
        int index = Math.abs(counter.getAndIncrement() % healthyNodes.size());
        return Optional.of(healthyNodes.get(index));
    }
}
