package com.lite_k8s.node;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class Node {

    private String id;
    private String name;
    private String host;
    private int port;

    @Builder.Default
    private NodeConnectionType connectionType = NodeConnectionType.TCP;
    private int sshPort;
    private String sshUser;
    private String sshKeyPath;

    @Builder.Default
    private NodeStatus status = NodeStatus.UNKNOWN;

    public boolean isSsh() {
        return NodeConnectionType.SSH.equals(connectionType);
    }

    @Builder.Default
    private double cpuUsagePercent = 0.0;

    @Builder.Default
    private double memoryUsagePercent = 0.0;

    @Builder.Default
    private int runningContainers = 0;

    private LocalDateTime lastHeartbeat;
}
