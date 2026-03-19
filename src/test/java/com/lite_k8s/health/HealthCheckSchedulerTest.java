package com.lite_k8s.health;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.lite_k8s.service.DockerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HealthCheckSchedulerTest {

    @Mock private ProbeRunner probeRunner;
    @Mock private DockerClient dockerClient;
    @Mock private DockerService dockerService;
    @Mock private ListContainersCmd listContainersCmd;
    @Mock private InspectContainerCmd inspectContainerCmd;
    @Mock private InspectContainerResponse inspectResponse;
    @Mock private com.github.dockerjava.api.model.NetworkSettings networkSettings;

    private HealthCheckProperties properties;
    private HealthCheckStateTracker stateTracker;
    private HealthCheckScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new HealthCheckProperties();
        stateTracker = new HealthCheckStateTracker();
        scheduler = new HealthCheckScheduler(properties, probeRunner, dockerClient, dockerService, stateTracker);

        // 컨테이너 inspect 기본 설정
        setupContainerInspect("172.17.0.2");
    }

    @Test
    @DisplayName("비활성화 시 probe 실행 안 함")
    void runProbes_WhenDisabled_DoesNothing() {
        properties.setEnabled(false);

        scheduler.runProbes();

        verifyNoInteractions(probeRunner, dockerClient);
    }

    @Test
    @DisplayName("probe 성공 시 재시작 안 함")
    void runProbes_WhenProbeSucceeds_DoesNotRestart() {
        setupEnabledWithHttpProbe("demo-.*", 8080);
        Container container = setupRunningContainer("c1", "/demo-api");

        when(probeRunner.run(eq("172.17.0.2"), any(), any()))
                .thenReturn(ProbeResult.success(50));

        scheduler.runProbes();

        verify(dockerService, never()).restartContainer(anyString(), any());
    }

    @Test
    @DisplayName("probe 연속 실패가 failureThreshold 미만이면 재시작 안 함")
    void runProbes_WhenFailuresBelowThreshold_DoesNotRestart() {
        setupEnabledWithHttpProbe("demo-.*", 8080);
        setupRunningContainer("c1", "/demo-api");

        when(probeRunner.run(any(), any(), any()))
                .thenReturn(ProbeResult.failure("connection refused"));

        // failureThreshold=3인데 2번만 실패
        scheduler.runProbes();
        scheduler.runProbes();

        verify(dockerService, never()).restartContainer(anyString(), any());
    }

    @Test
    @DisplayName("probe 연속 실패가 failureThreshold 이상이면 재시작")
    void runProbes_WhenFailuresReachThreshold_RestartsContainer() {
        setupEnabledWithHttpProbe("demo-.*", 8080);
        setupRunningContainer("c1", "/demo-api");

        when(probeRunner.run(any(), any(), any()))
                .thenReturn(ProbeResult.failure("connection refused"));
        when(dockerService.restartContainer("c1", dockerClient)).thenReturn(true);

        // failureThreshold=3번 실패 → 재시작
        scheduler.runProbes();
        scheduler.runProbes();
        scheduler.runProbes();

        verify(dockerService).restartContainer("c1", dockerClient);
    }

    @Test
    @DisplayName("재시작 후 실패 카운트 리셋")
    void runProbes_AfterRestart_ResetsFailureCount() {
        setupEnabledWithHttpProbe("demo-.*", 8080);
        setupRunningContainer("c1", "/demo-api");

        when(probeRunner.run(any(), any(), any()))
                .thenReturn(ProbeResult.failure("connection refused"));
        when(dockerService.restartContainer("c1", dockerClient)).thenReturn(true);

        // 3번 실패 → 재시작
        scheduler.runProbes();
        scheduler.runProbes();
        scheduler.runProbes();

        verify(dockerService, times(1)).restartContainer("c1", dockerClient);

        // 다시 2번 실패해도 아직 threshold 미달
        scheduler.runProbes();
        scheduler.runProbes();

        verify(dockerService, times(1)).restartContainer("c1", dockerClient); // 여전히 1번만
    }

    @Test
    @DisplayName("패턴 불일치 컨테이너는 probe 건너뜀")
    void runProbes_WhenContainerNotMatchingPattern_SkipsProbe() {
        setupEnabledWithHttpProbe("demo-.*", 8080);
        setupRunningContainer("c1", "/other-service");

        scheduler.runProbes();

        verifyNoInteractions(probeRunner);
    }

    private void setupEnabledWithHttpProbe(String pattern, int port) {
        properties.setEnabled(true);

        HealthCheckProperties.ContainerProbeConfig config = new HealthCheckProperties.ContainerProbeConfig();
        config.setContainerPattern(pattern);

        ProbeConfig liveness = ProbeConfig.builder()
                .type(ProbeType.HTTP)
                .port(port)
                .path("/actuator/health")
                .initialDelaySeconds(0)
                .periodSeconds(10)
                .failureThreshold(3)
                .build();
        config.setLiveness(liveness);

        properties.setProbes(List.of(config));
    }

    private Container setupRunningContainer(String id, String name) {
        Container container = mock(Container.class);
        when(container.getId()).thenReturn(id);
        when(container.getNames()).thenReturn(new String[]{name});

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));

        return container;
    }

    private void setupContainerInspect(String ip) {
        when(networkSettings.getIpAddress()).thenReturn(ip);
        when(inspectResponse.getNetworkSettings()).thenReturn(networkSettings);
        when(inspectContainerCmd.exec()).thenReturn(inspectResponse);
        when(dockerClient.inspectContainerCmd(anyString())).thenReturn(inspectContainerCmd);
    }
}
