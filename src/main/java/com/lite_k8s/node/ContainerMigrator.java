package com.lite_k8s.node;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.lite_k8s.util.DockerContainerNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 장애 노드의 컨테이너를 healthy 노드로 이동
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContainerMigrator {

    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory clientFactory;
    private final PlacementStrategy placementStrategy;

    @EventListener
    public void onNodeFailure(NodeFailureEvent event) {
        Node failedNode = event.getFailedNode();
        log.warn("노드 장애 감지 → 컨테이너 마이그레이션 시작: {}", failedNode.getName());

        List<Node> healthyNodes = nodeRegistry.findHealthy();
        if (healthyNodes.isEmpty()) {
            log.error("마이그레이션 불가: 가용한 healthy 노드 없음");
            return;
        }

        // 장애 노드의 실행 중 컨테이너 목록 조회
        List<Container> containers = getContainersFromFailedNode(failedNode);
        if (containers.isEmpty()) {
            log.info("마이그레이션할 컨테이너 없음: {}", failedNode.getName());
            return;
        }

        log.info("마이그레이션 대상: {}개 컨테이너", containers.size());

        for (Container container : containers) {
            Optional<Node> target = placementStrategy.selectNode(healthyNodes);
            if (target.isEmpty()) {
                log.error("배치 노드 선택 실패: {}", extractName(container));
                continue;
            }
            migrateContainer(failedNode, target.get(), container);
        }
    }

    private List<Container> getContainersFromFailedNode(Node failedNode) {
        try {
            DockerClient client = clientFactory.createClient(failedNode);
            return client.listContainersCmd().withShowAll(false).exec();
        } catch (Exception e) {
            log.warn("장애 노드 컨테이너 목록 조회 실패: {}", failedNode.getName());
            return List.of();
        }
    }

    private void migrateContainer(Node sourceNode, Node targetNode, Container container) {
        String name = extractName(container);
        log.info("컨테이너 이동: {} → {} ({})", name, targetNode.getName(), container.getId());

        try {
            // 소스 노드에서 컨테이너 설정 조회
            DockerClient sourceClient = clientFactory.createClient(sourceNode);
            InspectContainerResponse inspect = sourceClient.inspectContainerCmd(container.getId()).exec();

            String image = inspect.getConfig().getImage();
            String[] env = inspect.getConfig().getEnv() != null ? inspect.getConfig().getEnv() : new String[]{};
            HostConfig hostConfig = inspect.getHostConfig() != null
                    ? inspect.getHostConfig() : HostConfig.newHostConfig();

            // 타겟 노드에 컨테이너 생성 및 시작
            DockerClient targetClient = clientFactory.createClient(targetNode);
            CreateContainerResponse created = targetClient.createContainerCmd(image)
                    .withName(name)
                    .withHostConfig(hostConfig)
                    .withEnv(env)
                    .withLabels(inspect.getConfig().getLabels() != null
                            ? inspect.getConfig().getLabels() : java.util.Map.of())
                    .exec();

            targetClient.startContainerCmd(created.getId()).exec();
            log.info("마이그레이션 완료: {} → {} ({})", name, targetNode.getName(), created.getId());

        } catch (Exception e) {
            log.error("컨테이너 마이그레이션 실패: {} → {}", name, targetNode.getName(), e);
        }
    }

    private String extractName(Container container) {
        return DockerContainerNames.extractName(container, "unknown");
    }
}
