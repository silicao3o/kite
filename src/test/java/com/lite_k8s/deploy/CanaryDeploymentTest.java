package com.lite_k8s.deploy;

import com.github.dockerjava.api.DockerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CanaryDeploymentTest {

    @Mock private DockerClient dockerClient;
    @Mock private ContainerOperator operator;

    private CanaryDeployment strategy;

    @BeforeEach
    void setUp() {
        strategy = new CanaryDeployment(operator);
    }

    @Test
    @DisplayName("weight=20%, 5개 컨테이너 → 1개만 canary 배포")
    void deploy_20PercentWeight_DeploysOneOutOfFive() {
        DeploymentSpec spec = DeploymentSpec.builder()
                .serviceName("web")
                .newImage("web:v2")
                .canaryWeight(20)
                .targets(List.of(
                        container("c1", "web-1"),
                        container("c2", "web-2"),
                        container("c3", "web-3"),
                        container("c4", "web-4"),
                        container("c5", "web-5")))
                .build();

        when(operator.createAndStart(anyString(), eq("web:v2"), contains("-canary")))
                .thenReturn("canary-id");

        DeployResult result = strategy.deploy(spec, dockerClient);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStrategy()).isEqualTo(DeploymentType.CANARY);
        assertThat(result.getDeployed()).isEqualTo(1); // 1개만 canary

        // 기존 4개는 그대로 → stopAndRemove 안 함
        verify(operator, times(1)).createAndStart(anyString(), eq("web:v2"), anyString());
        verify(operator, never()).stopAndRemove(anyString());
    }

    @Test
    @DisplayName("weight=50%, 4개 컨테이너 → 2개 canary")
    void deploy_50PercentWeight_DeploysTwoOutOfFour() {
        DeploymentSpec spec = DeploymentSpec.builder()
                .serviceName("web")
                .newImage("web:v2")
                .canaryWeight(50)
                .targets(List.of(
                        container("c1", "web-1"),
                        container("c2", "web-2"),
                        container("c3", "web-3"),
                        container("c4", "web-4")))
                .build();

        when(operator.createAndStart(anyString(), eq("web:v2"), contains("-canary")))
                .thenReturn("canary-id");

        DeployResult result = strategy.deploy(spec, dockerClient);

        assertThat(result.getDeployed()).isEqualTo(2);
    }

    @Test
    @DisplayName("weight=100% → 전체 canary (사실상 rolling update)")
    void deploy_100PercentWeight_DeploysAll() {
        DeploymentSpec spec = DeploymentSpec.builder()
                .serviceName("web")
                .newImage("web:v2")
                .canaryWeight(100)
                .targets(List.of(
                        container("c1", "web-1"),
                        container("c2", "web-2")))
                .build();

        when(operator.createAndStart(anyString(), eq("web:v2"), contains("-canary")))
                .thenReturn("canary-id");

        DeployResult result = strategy.deploy(spec, dockerClient);

        assertThat(result.getDeployed()).isEqualTo(2);
    }

    @Test
    @DisplayName("canary 컨테이너는 원본과 병행 실행 (기존 컨테이너 미제거)")
    void deploy_CanaryContainersRunAlongside_OriginalNotRemoved() {
        DeploymentSpec spec = DeploymentSpec.builder()
                .serviceName("web")
                .newImage("web:v2")
                .canaryWeight(33)
                .targets(List.of(
                        container("c1", "web-1"),
                        container("c2", "web-2"),
                        container("c3", "web-3")))
                .build();

        when(operator.createAndStart(anyString(), anyString(), anyString())).thenReturn("canary-id");

        strategy.deploy(spec, dockerClient);

        // 원본 컨테이너 제거 없음
        verify(operator, never()).stopAndRemove(anyString());
    }

    private DeploymentSpec.RunningContainer container(String id, String name) {
        return DeploymentSpec.RunningContainer.builder()
                .id(id).name(name).currentImage("web:v1").build();
    }
}
