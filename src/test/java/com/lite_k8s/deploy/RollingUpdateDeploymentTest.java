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
class RollingUpdateDeploymentTest {

    @Mock private DockerClient dockerClient;
    @Mock private ContainerOperator operator;

    private RollingUpdateDeployment strategy;

    @BeforeEach
    void setUp() {
        strategy = new RollingUpdateDeployment(operator);
    }

    @Test
    @DisplayName("maxUnavailable=1이면 컨테이너 하나씩 교체")
    void deploy_MaxUnavailable1_ReplacesOneByOne() {
        DeploymentSpec spec = DeploymentSpec.builder()
                .serviceName("web")
                .newImage("web:v2")
                .maxUnavailable(1)
                .targets(List.of(
                        container("c1", "web-1"),
                        container("c2", "web-2"),
                        container("c3", "web-3")))
                .build();

        when(operator.stopAndRemove(anyString())).thenReturn(true);
        when(operator.createAndStart(anyString(), anyString(), anyString())).thenReturn("new-id");

        DeployResult result = strategy.deploy(spec, dockerClient);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDeployed()).isEqualTo(3);
        assertThat(result.getStrategy()).isEqualTo(DeploymentType.ROLLING_UPDATE);

        // 3번 교체
        verify(operator, times(3)).stopAndRemove(anyString());
        verify(operator, times(3)).createAndStart(anyString(), eq("web:v2"), anyString());
    }

    @Test
    @DisplayName("컨테이너 생성 실패 시 실패 카운트 증가")
    void deploy_WhenCreateFails_RecordsFailure() {
        DeploymentSpec spec = DeploymentSpec.builder()
                .serviceName("web")
                .newImage("web:v2")
                .maxUnavailable(1)
                .targets(List.of(container("c1", "web-1")))
                .build();

        when(operator.stopAndRemove(anyString())).thenReturn(true);
        when(operator.createAndStart(anyString(), anyString(), anyString())).thenReturn(null);

        DeployResult result = strategy.deploy(spec, dockerClient);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailed()).isEqualTo(1);
    }

    @Test
    @DisplayName("빈 targets → 즉시 성공")
    void deploy_EmptyTargets_ReturnsSuccess() {
        DeploymentSpec spec = DeploymentSpec.builder()
                .serviceName("web").newImage("web:v2")
                .targets(List.of()).build();

        DeployResult result = strategy.deploy(spec, dockerClient);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDeployed()).isEqualTo(0);
        verifyNoInteractions(operator);
    }

    private DeploymentSpec.RunningContainer container(String id, String name) {
        return DeploymentSpec.RunningContainer.builder()
                .id(id).name(name).currentImage("web:v1").build();
    }
}
