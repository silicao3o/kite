package com.lite_k8s.node;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class NodeConfig {

    private final NodeProperties properties;
    private final NodeRegistry registry;

    @PostConstruct
    public void registerNodes() {
        if (!properties.isEnabled()) return;
        if (properties.getNodes() == null || properties.getNodes().isEmpty()) return;

        for (NodeProperties.NodeConfig cfg : properties.getNodes()) {
            NodeConnectionType connectionType = parseConnectionType(cfg.getConnectionType());

            if (connectionType == NodeConnectionType.SSH_PROXY && properties.getProxy() == null) {
                log.warn("노드 '{}' 는 SSH_PROXY 타입이지만 proxy 설정이 없습니다. 건너뜁니다.", cfg.getName());
                continue;
            }

            Node node = Node.builder()
                    .id(UUID.randomUUID().toString())
                    .name(cfg.getName())
                    .host(cfg.getHost())
                    .port(cfg.getPort())
                    .connectionType(connectionType)
                    .sshPort(cfg.getSshPort())
                    .sshUser(cfg.getSshUser())
                    .sshKeyPath(cfg.getSshKeyPath())
                    .status(NodeStatus.UNKNOWN)
                    .build();
            registry.register(node);
            log.info("노드 등록 (startup): {} ({}://{}:{})",
                    cfg.getName(), cfg.getConnectionType(), cfg.getHost(), cfg.getPort());
        }

        if (properties.getProxy() != null) {
            log.info("프록시(CP) 설정: {}@{}:{}",
                    properties.getProxy().getUser(),
                    properties.getProxy().getHost(),
                    properties.getProxy().getPort());
        }
        log.info("총 {}개 노드 등록 완료", registry.findAll().size());
    }

    private NodeConnectionType parseConnectionType(String type) {
        return switch (type.toUpperCase()) {
            case "SSH" -> NodeConnectionType.SSH;
            case "SSH_PROXY" -> NodeConnectionType.SSH_PROXY;
            default -> NodeConnectionType.TCP;
        };
    }

    @Bean
    public PlacementStrategy placementStrategy() {
        return switch (properties.getPlacementStrategy().toUpperCase()) {
            case "ROUND_ROBIN" -> new RoundRobinStrategy();
            default -> new LeastUsedStrategy();
        };
    }
}
