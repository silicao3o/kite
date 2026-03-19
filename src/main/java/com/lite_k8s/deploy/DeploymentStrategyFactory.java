package com.lite_k8s.deploy;

import com.github.dockerjava.api.DockerClient;
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

    public DeploymentStrategy create(DeploymentType type) {
        ContainerOperator operator = new ContainerOperator(dockerClient);
        return switch (type) {
            case ROLLING_UPDATE -> new RollingUpdateDeployment(operator);
            case RECREATE -> new RecreateDeployment(operator);
            case BLUE_GREEN -> new BlueGreenDeployment(operator);
            case CANARY -> new CanaryDeployment(operator);
        };
    }
}
