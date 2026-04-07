package com.lite_k8s.deploy;

import com.github.dockerjava.api.DockerClient;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 배포 전략 팩토리
 * DeploymentType에 따라 적절한 전략 인스턴스 반환
 */
@Component
@RequiredArgsConstructor
public class DeploymentStrategyFactory {

    private final DockerClient dockerClient;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;

    public DeploymentStrategy create(DeploymentType type) {
        return create(type, null);
    }

    public DeploymentStrategy create(DeploymentType type, String nodeId) {
        ContainerOperator operator = createOperator(nodeId);
        return switch (type) {
            case ROLLING_UPDATE -> new RollingUpdateDeployment(operator);
            case RECREATE -> new RecreateDeployment(operator);
            case BLUE_GREEN -> new BlueGreenDeployment(operator);
            case CANARY -> new CanaryDeployment(operator);
        };
    }

    public ContainerOperator createOperator(String nodeId) {
        DockerClient client = nodeId == null ? dockerClient
                : nodeRegistry.findById(nodeId)
                        .map(nodeClientFactory::createClient)
                        .orElse(dockerClient);
        return new ContainerOperator(client);
    }
}
