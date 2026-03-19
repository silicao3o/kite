package com.lite_k8s.node;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 다중 노드 설정
 *
 * 예시:
 * docker.monitor.nodes:
 *   enabled: true
 *   heartbeat-interval-seconds: 30
 *   placement-strategy: LEAST_USED   # LEAST_USED | ROUND_ROBIN
 *   nodes:
 *     - name: server-1
 *       host: 192.168.1.10
 *       port: 2375
 *     - name: server-2
 *       host: 192.168.1.11
 *       port: 2375
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docker.monitor.nodes")
public class NodeProperties {

    private boolean enabled = false;
    private int heartbeatIntervalSeconds = 30;
    private String placementStrategy = "LEAST_USED";
    private List<NodeConfig> nodes = new ArrayList<>();

    @Getter
    @Setter
    public static class NodeConfig {
        private String name;
        private String host;
        private int port = 2375;
        // SSH 터널 설정 (connectionType: SSH 일 때만 사용)
        private String connectionType = "TCP";
        private int sshPort = 22;
        private String sshUser;
        private String sshKeyPath;
    }
}
