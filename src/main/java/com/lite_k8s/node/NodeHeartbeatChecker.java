package com.lite_k8s.node;

import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

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
            client.listContainersCmd().withShowAll(false).exec();

            nodeRegistry.updateStatus(node.getId(), NodeStatus.HEALTHY);
            log.debug("[Heartbeat] {} → HEALTHY", node.getName());

        } catch (Exception e) {
            boolean wasHealthy = NodeStatus.HEALTHY.equals(node.getStatus());

            nodeRegistry.updateStatus(node.getId(), NodeStatus.UNHEALTHY);
            log.warn("[Heartbeat] {} → UNHEALTHY: {}", node.getName(), e.getMessage());

            // 이전에 HEALTHY였을 때만 이벤트 발행 (중복 방지)
            if (wasHealthy) {
                eventPublisher.publishEvent(new NodeFailureEvent(node));
            }
        }
    }
}
