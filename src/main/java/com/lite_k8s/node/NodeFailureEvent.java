package com.lite_k8s.node;

import lombok.Getter;

@Getter
public class NodeFailureEvent {
    private final Node failedNode;

    public NodeFailureEvent(Node failedNode) {
        this.failedNode = failedNode;
    }
}
