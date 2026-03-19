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

        for (NodeProperties.NodeConfig cfg : properties.getNodes()) {
            Node node = Node.builder()
                    .id(UUID.randomUUID().toString())
                    .name(cfg.getName())
                    .host(cfg.getHost())
                    .port(cfg.getPort())
                    .status(NodeStatus.UNKNOWN)
                    .build();
            registry.register(node);
        }
        log.info("{}개 노드 등록 완료", properties.getNodes().size());
    }

    @Bean
    public PlacementStrategy placementStrategy() {
        return switch (properties.getPlacementStrategy().toUpperCase()) {
            case "ROUND_ROBIN" -> new RoundRobinStrategy();
            default -> new LeastUsedStrategy();
        };
    }
}
