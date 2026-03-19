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
class BlueGreenDeploymentTest {

    @Mock private DockerClient dockerClient;
    @Mock private ContainerOperator operator;

    private BlueGreenDeployment strategy;

    @BeforeEach
    void setUp() {
        // blueGreenWaitMs=0으로 대기 없이 테스트
        strategy = new BlueGreenDeployment(operator, 0);
    }

    @Test
    @DisplayName("Green 성공 시 Blue 제거 → BLUE_GREEN 타입 반환")
    void deploy_WhenGreenSucceeds_RemovesBlue() {
        DeploymentSpec spec = DeploymentSpec.builder()
                .serviceName("web")
                .newImage("web:v2")
                .blueGreenWaitMs(0)
                .targets(List.of(
                        container("c1", "web-1"),
                        container("c2", "web-2")))
                .build();

        String greenId1 = "green-c1";
        String greenId2 = "green-c2";

        when(operator.createAndStart(eq("c1"), eq("web:v2"), eq("web-1-green"))).thenReturn(greenId1);
        when(operator.createAndStart(eq("c2"), eq("web:v2"), eq("web-2-green"))).thenReturn(greenId2);
        when(operator.isRunning(greenId1)).thenReturn(true);
        when(operator.isRunning(greenId2)).thenReturn(true);
        when(operator.stopAndRemove(anyString())).thenReturn(true);

        DeployResult result = strategy.deploy(spec, dockerClient);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStrategy()).isEqualTo(DeploymentType.BLUE_GREEN);

        // Blue(c1, c2) 제거됨
        verify(operator).stopAndRemove("c1");
        verify(operator).stopAndRemove("c2");
    }

    @Test
    @DisplayName("Green 컨테이너 생성 실패 시 롤백 (Green 제거) 후 실패 반환")
    void deploy_WhenGreenFails_RollsBack() {
        DeploymentSpec spec = DeploymentSpec.builder()
                .serviceName("web")
                .newImage("web:v2")
                .blueGreenWaitMs(0)
                .targets(List.of(container("c1", "web-1")))
                .build();

        when(operator.createAndStart(anyString(), anyString(), anyString())).thenReturn(null); // 생성 실패

        DeployResult result = strategy.deploy(spec, dockerClient);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStrategy()).isEqualTo(DeploymentType.BLUE_GREEN);
        // Blue는 유지 (제거 안 함)
        verify(operator, never()).stopAndRemove("c1");
    }

    @Test
    @DisplayName("Green 실행 실패(unhealthy) 시 롤백")
    void deploy_WhenGreenNotRunning_RollsBack() {
        DeploymentSpec spec = DeploymentSpec.builder()
                .serviceName("web")
                .newImage("web:v2")
                .blueGreenWaitMs(0)
                .targets(List.of(container("c1", "web-1")))
                .build();

        when(operator.createAndStart(anyString(), anyString(), anyString())).thenReturn("green-c1");
        when(operator.isRunning("green-c1")).thenReturn(false); // green이 실행 안 됨

        DeployResult result = strategy.deploy(spec, dockerClient);

        assertThat(result.isSuccess()).isFalse();
        // green 제거 (롤백)
        verify(operator).stopAndRemove("green-c1");
        // blue는 유지
        verify(operator, never()).stopAndRemove("c1");
    }

    private DeploymentSpec.RunningContainer container(String id, String name) {
        return DeploymentSpec.RunningContainer.builder()
                .id(id).name(name).currentImage("web:v1").build();
    }
}
