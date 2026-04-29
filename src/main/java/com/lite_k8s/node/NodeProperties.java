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

    /**
     * 노드/프록시 SSH 키 패스프레이즈 디폴트.
     * 노드 등록 시 별도 패스프레이즈를 받지 않으며, 모든 SSH 인증에서
     * 명시적 값이 비어 있으면 이 값으로 fallback.
     */
    private String defaultSshPassphrase = "skybmw1004";

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
