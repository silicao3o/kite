package com.lite_k8s.node;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class NodeRegistry {

    private final NodeJpaRepository jpa;

    // 런타임 메트릭은 인메모리 유지 (CPU, 메모리, 컨테이너 수, heartbeat — 재시작 후 재수집)
    private final ConcurrentHashMap<String, Node> runtimeCache = new ConcurrentHashMap<>();

    public void register(Node node) {
        jpa.save(node);
        runtimeCache.put(node.getId(), node);
        log.info("노드 등록: {} ({}:{})", node.getName(), node.getHost(), node.getPort());
    }

    public boolean registerIfAbsent(Node node) {
        return jpa.findByName(node.getName()).map(existing -> {
            runtimeCache.put(existing.getId(), existing);
            log.info("노드 이미 존재, 스킵: {}", existing.getName());
            return false;
        }).orElseGet(() -> {
            register(node);
            return true;
        });
    }

    public void unregister(String nodeId) {
        jpa.deleteById(nodeId);
        Node removed = runtimeCache.remove(nodeId);
        if (removed != null) {
            log.info("노드 해제: {}", removed.getName());
        }
    }

    public Optional<Node> findByName(String name) {
        return runtimeCache.values().stream()
                .filter(n -> name.equals(n.getName()))
                .findFirst()
                .or(() -> jpa.findByName(name).map(n -> {
                    runtimeCache.put(n.getId(), n);
                    return n;
                }));
    }

    public Optional<Node> findById(String nodeId) {
        Node cached = runtimeCache.get(nodeId);
        if (cached != null) return Optional.of(cached);
        return jpa.findById(nodeId).map(n -> {
            runtimeCache.put(n.getId(), n);
            return n;
        });
    }

    public List<Node> findAll() {
        List<Node> nodes = jpa.findAll();
        nodes.forEach(n -> runtimeCache.put(n.getId(), n));
        return nodes;
    }

    public List<Node> findHealthy() {
        return findAll().stream()
                .filter(n -> NodeStatus.HEALTHY.equals(n.getStatus()))
                .toList();
    }

    public void updateStatus(String nodeId, NodeStatus status) {
        findById(nodeId).ifPresent(node -> {
            node.setStatus(status);
            jpa.save(node);
        });
    }

    public void updateMetrics(String nodeId, double cpu, double memory, int containers) {
        Node node = runtimeCache.get(nodeId);
        if (node != null) {
            node.setCpuUsagePercent(cpu);
            node.setMemoryUsagePercent(memory);
            node.setRunningContainers(containers);
            node.setLastHeartbeat(java.time.LocalDateTime.now());
        }
    }
}
