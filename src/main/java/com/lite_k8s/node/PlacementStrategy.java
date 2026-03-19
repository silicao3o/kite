package com.lite_k8s.node;

import java.util.List;
import java.util.Optional;

public interface PlacementStrategy {
    Optional<Node> selectNode(List<Node> healthyNodes);
}
