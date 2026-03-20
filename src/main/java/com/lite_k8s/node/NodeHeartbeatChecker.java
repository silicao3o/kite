package com.lite_k8s.node;

import com.lite_k8s.model.ContainerMetrics;
import com.lite_k8s.service.MetricsCollector;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Info;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 노드 Heartbeat 감지
 * 각 노드의 Docker API를 주기적으로 호출하여 생존 여부 확인
 * 실패 시 UNHEALTHY 마크 + NodeFailureEvent 발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeHeartbeatChecker {

    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory clientFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final MetricsCollector metricsCollector;

    @Scheduled(fixedDelayString =
            "#{${docker.monitor.nodes.heartbeat-interval-seconds:30} * 1000}")
    public void checkHeartbeats() {
        List<Node> nodes = nodeRegistry.findAll();
        if (nodes.isEmpty()) return;

        for (Node node : nodes) {
            checkNode(node);
        }
    }

    private void checkNode(Node node) {
        try {
            DockerClient client = clientFactory.createClient(node);
            List<Container> running = client.listContainersCmd().withShowAll(false).exec();

            double totalCpu = 0.0;
            long totalMemUsage = 0L;
            for (Container c : running) {
                Optional<ContainerMetrics> m = metricsCollector.collectMetrics(c.getId(),
                        c.getNames() != null && c.getNames().length > 0 ? c.getNames()[0] : c.getId(),
                        client);
                if (m.isPresent()) {
                    totalCpu += m.get().getCpuPercent();
                    totalMemUsage += m.get().getMemoryUsage();
                }
            }

            Info info = client.infoCmd().exec();
            long memTotal = info.getMemTotal() != null ? info.getMemTotal() : 1L;
            double memPercent = memTotal > 0 ? (totalMemUsage * 100.0 / memTotal) : 0.0;

            nodeRegistry.updateMetrics(node.getId(), totalCpu, memPercent, running.size());
            nodeRegistry.updateStatus(node.getId(), NodeStatus.HEALTHY);
            log.debug("[Heartbeat] {} → HEALTHY (컨테이너 {}개, CPU {:.1f}%, MEM {:.1f}%)",
                    node.getName(), running.size(), totalCpu, memPercent);

        } catch (Exception e) {
            boolean wasHealthy = NodeStatus.HEALTHY.equals(node.getStatus());

            nodeRegistry.updateStatus(node.getId(), NodeStatus.UNHEALTHY);
            log.warn("[Heartbeat] {} → UNHEALTHY: {}", node.getName(), e.getMessage(), e);

            // 이전에 HEALTHY였을 때만 이벤트 발행 (중복 방지)
            if (wasHealthy) {
                eventPublisher.publishEvent(new NodeFailureEvent(node));
            }
        }
    }
}
