package com.lite_k8s.node;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docker.monitor.nodes")
public class NodeProperties {

    private boolean enabled = false;
    private int heartbeatIntervalSeconds = 30;
    private String placementStrategy = "LEAST_USED";
    private ProxyConfig proxy;
    private List<NodeConfig> nodes = new ArrayList<>();

    @Getter
    @Setter
    public static class ProxyConfig {
        private String host;
        private Integer port = 22;
        private String user;
        private String keyPath;
        private String passphrase;
    }

    @Getter
    @Setter
    public static class NodeConfig {
        private String name;
        private String host;
        private Integer port = 2375;
        private String connectionType = "SSH";
        private Integer sshPort = 22;
        private String sshUser;
        private String sshPassword;
        private String sshKeyPath;
        private String sshPassphrase;
    }
}
