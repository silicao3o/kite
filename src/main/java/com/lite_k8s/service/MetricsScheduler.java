package com.lite_k8s.service;

import com.lite_k8s.config.MonitorProperties;
import com.lite_k8s.metrics.MetricsHistoryService;
import com.lite_k8s.model.ContainerInfo;
import com.lite_k8s.model.ContainerMetrics;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsScheduler {

    private final DockerService dockerService;
    private final MetricsCollector metricsCollector;
    private final MonitorProperties monitorProperties;
    private final MetricsHistoryService metricsHistoryService;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;
    private final DockerClient localDockerClient;

    private final Map<String, ContainerMetrics> metricsCache = new ConcurrentHashMap<>();
    private volatile List<ContainerInfo> containerCache = List.of();

    @Scheduled(fixedRateString = "${docker.monitor.metrics.collection-interval-seconds:15}000")
    public void collectMetrics() {
        if (!monitorProperties.getMetrics().isEnabled()) {
            log.debug("메트릭 수집이 비활성화되어 있습니다");
            return;
        }

        log.debug("메트릭 수집 시작");

        List<ContainerInfo> containers = dockerService.listContainers(true);
        containerCache = List.copyOf(containers);

        containers.stream()
                .filter(container -> "running".equalsIgnoreCase(container.getState()))
                .forEach(this::collectAndCacheMetrics);

        log.debug("메트릭 수집 완료. 캐시 크기: {}", metricsCache.size());
    }

    private void collectAndCacheMetrics(ContainerInfo container) {
        DockerClient client = resolveClient(container.getNodeId());
        metricsCollector.collectMetrics(container.getId(), container.getName(), client)
                .ifPresent(metrics -> {
                    metricsCache.put(container.getId(), metrics);
                    metricsHistoryService.record(metrics);
                    log.trace("메트릭 캐시 저장: {} (CPU: {}%, Memory: {}%)",
                            container.getName(),
                            String.format("%.1f", metrics.getCpuPercent()),
                            String.format("%.1f", metrics.getMemoryPercent()));
                });
    }

    private DockerClient resolveClient(String nodeId) {
        if (nodeId == null) return localDockerClient;
        return nodeRegistry.findById(nodeId)
                .map(nodeClientFactory::createClient)
                .orElse(localDockerClient);
    }

    public List<ContainerInfo> getCachedContainers() {
        return containerCache;
    }

    public Optional<ContainerMetrics> getLatestMetrics(String containerId) {
        return Optional.ofNullable(metricsCache.get(containerId));
    }

    public int getCollectionIntervalSeconds() {
        return monitorProperties.getMetrics().getCollectionIntervalSeconds();
    }

    public Map<String, ContainerMetrics> getAllCachedMetrics() {
        return Map.copyOf(metricsCache);
    }

    public void clearCache() {
        metricsCache.clear();
    }
}
