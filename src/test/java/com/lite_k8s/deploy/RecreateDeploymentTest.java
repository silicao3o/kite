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
class RecreateDeploymentTest {

    @Mock private DockerClient dockerClient;
    @Mock private ContainerOperator operator;

    private RecreateDeployment strategy;

    @BeforeEach
    void setUp() {
        strategy = new RecreateDeployment(operator);
    }

    @Test
    @DisplayName("전체 중지 후 전체 생성 순서로 실행")
    void deploy_StopsAllThenCreatesAll() {
        DeploymentSpec spec = DeploymentSpec.builder()
                .serviceName("web")
                .newImage("web:v2")
                .targets(List.of(
                        container("c1", "web-1"),
                        container("c2", "web-2")))
                .build();

        when(operator.stopAndRemove(anyString())).thenReturn(true);
        when(operator.createAndStart(anyString(), anyString(), anyString())).thenReturn("new-id");

        DeployResult result = strategy.deploy(spec, dockerClient);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStrategy()).isEqualTo(DeploymentType.RECREATE);

        // 먼저 2개 중지, 그다음 2개 생성
        var inOrder = inOrder(operator);
        inOrder.verify(operator, times(2)).stopAndRemove(anyString());
        inOrder.verify(operator, times(2)).createAndStart(anyString(), eq("web:v2"), anyString());
    }

    @Test
    @DisplayName("중지 실패해도 생성 시도")
    void deploy_WhenStopFails_StillCreates() {
        DeploymentSpec spec = DeploymentSpec.builder()
                .serviceName("web")
                .newImage("web:v2")
                .targets(List.of(container("c1", "web-1")))
                .build();

        when(operator.stopAndRemove(anyString())).thenReturn(false); // 중지 실패
        when(operator.createAndStart(anyString(), anyString(), anyString())).thenReturn("new-id");

        DeployResult result = strategy.deploy(spec, dockerClient);

        // 생성은 시도함
        verify(operator).createAndStart(anyString(), eq("web:v2"), anyString());
        assertThat(result.getDeployed()).isEqualTo(1);
    }

    private DeploymentSpec.RunningContainer container(String id, String name) {
        return DeploymentSpec.RunningContainer.builder()
                .id(id).name(name).currentImage("web:v1").build();
    }
}
