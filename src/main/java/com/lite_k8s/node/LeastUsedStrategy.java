package com.lite_k8s.node;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * CPU + 메모리 합산이 가장 낮은 노드 선택
 */
public class LeastUsedStrategy implements PlacementStrategy {

    @Override
    public Optional<Node> selectNode(List<Node> healthyNodes) {
        return healthyNodes.stream()
                .min(Comparator.comparingDouble(n -> n.getCpuUsagePercent() + n.getMemoryUsagePercent()));
    }
}
