package com.lite_k8s.node;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class NodeRegistry {

    private final ConcurrentHashMap<String, Node> nodes = new ConcurrentHashMap<>();

    public void register(Node node) {
        nodes.put(node.getId(), node);
        log.info("노드 등록: {} ({}:{})", node.getName(), node.getHost(), node.getPort());
    }

    public void unregister(String nodeId) {
        Node removed = nodes.remove(nodeId);
        if (removed != null) {
            log.info("노드 해제: {}", removed.getName());
        }
    }

    public Optional<Node> findById(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    public List<Node> findAll() {
        return new ArrayList<>(nodes.values());
    }

    public List<Node> findHealthy() {
        return nodes.values().stream()
                .filter(n -> NodeStatus.HEALTHY.equals(n.getStatus()))
                .toList();
    }

    public void updateStatus(String nodeId, NodeStatus status) {
        Node node = nodes.get(nodeId);
        if (node != null) {
            node.setStatus(status);
        }
    }

    public void updateMetrics(String nodeId, double cpu, double memory, int containers) {
        Node node = nodes.get(nodeId);
        if (node != null) {
            node.setCpuUsagePercent(cpu);
            node.setMemoryUsagePercent(memory);
            node.setRunningContainers(containers);
            node.setLastHeartbeat(java.time.LocalDateTime.now());
        }
    }
}
