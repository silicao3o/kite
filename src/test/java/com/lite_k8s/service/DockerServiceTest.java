package com.lite_k8s.service;

import com.lite_k8s.model.ContainerDeathEvent;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.model.ContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DockerServiceTest {

    @Mock
    private DockerClient dockerClient;

    @Mock
    private InspectContainerCmd inspectContainerCmd;

    @Mock
    private InspectContainerResponse inspectContainerResponse;

    @Mock
    private InspectContainerResponse.ContainerState containerState;

    @Mock
    private ContainerConfig containerConfig;

    @Mock
    private LogContainerCmd logContainerCmd;

    @Mock
    private StartContainerCmd startContainerCmd;

    @Mock
    private StopContainerCmd stopContainerCmd;

    private DockerService dockerService;

    @BeforeEach
    void setUp() {
        dockerService = new DockerService(dockerClient, null, null);
        ReflectionTestUtils.setField(dockerService, "logTailLines", 50);
    }

    @Test
    @DisplayName("컨테이너 종료 이벤트 빌드 - 정상 케이스")
    void buildDeathEvent_ShouldReturnCorrectEvent() {
        // given
        String containerId = "abc123def456";
        String action = "die";

        when(dockerClient.inspectContainerCmd(anyString())).thenReturn(inspectContainerCmd);
        when(inspectContainerCmd.exec()).thenReturn(inspectContainerResponse);
        when(inspectContainerResponse.getState()).thenReturn(containerState);
        when(inspectContainerResponse.getName()).thenReturn("/my-container");
        when(inspectContainerResponse.getConfig()).thenReturn(containerConfig);
        when(containerConfig.getImage()).thenReturn("nginx:latest");
        when(containerState.getExitCodeLong()).thenReturn(137L);
        when(containerState.getOOMKilled()).thenReturn(false);
        when(containerState.getFinishedAt()).thenReturn("2026-03-10T10:30:00Z");

        // 로그 명령 모킹
        when(dockerClient.logContainerCmd(anyString())).thenReturn(logContainerCmd);
        when(logContainerCmd.withStdOut(true)).thenReturn(logContainerCmd);
        when(logContainerCmd.withStdErr(true)).thenReturn(logContainerCmd);
        when(logContainerCmd.withTail(anyInt())).thenReturn(logContainerCmd);
        when(logContainerCmd.withTimestamps(true)).thenReturn(logContainerCmd);

        // when
        ContainerDeathEvent event = dockerService.buildDeathEvent(containerId, action);

        // then
        assertThat(event).isNotNull();
        assertThat(event.getContainerId()).isEqualTo(containerId);
        assertThat(event.getContainerName()).isEqualTo("my-container"); // "/" 제거됨
        assertThat(event.getImageName()).isEqualTo("nginx:latest");
        assertThat(event.getExitCode()).isEqualTo(137L);
        assertThat(event.isOomKilled()).isFalse();
        assertThat(event.getAction()).isEqualTo(action);
    }

    @Test
    @DisplayName("컨테이너 종료 이벤트 빌드 - OOM Killed 케이스")
    void buildDeathEvent_WhenOomKilled_ShouldSetOomKilledTrue() {
        // given
        String containerId = "abc123def456";

        when(dockerClient.inspectContainerCmd(anyString())).thenReturn(inspectContainerCmd);
        when(inspectContainerCmd.exec()).thenReturn(inspectContainerResponse);
        when(inspectContainerResponse.getState()).thenReturn(containerState);
        when(inspectContainerResponse.getName()).thenReturn("/oom-container");
        when(inspectContainerResponse.getConfig()).thenReturn(containerConfig);
        when(containerConfig.getImage()).thenReturn("java-app:1.0");
        when(containerState.getExitCodeLong()).thenReturn(137L);
        when(containerState.getOOMKilled()).thenReturn(true);
        when(containerState.getFinishedAt()).thenReturn("2026-03-10T10:30:00Z");

        when(dockerClient.logContainerCmd(anyString())).thenReturn(logContainerCmd);
        when(logContainerCmd.withStdOut(true)).thenReturn(logContainerCmd);
        when(logContainerCmd.withStdErr(true)).thenReturn(logContainerCmd);
        when(logContainerCmd.withTail(anyInt())).thenReturn(logContainerCmd);
        when(logContainerCmd.withTimestamps(true)).thenReturn(logContainerCmd);

        // when
        ContainerDeathEvent event = dockerService.buildDeathEvent(containerId, "oom");

        // then
        assertThat(event.isOomKilled()).isTrue();
        assertThat(event.getExitCode()).isEqualTo(137L);
    }

    @Test
    @DisplayName("컨테이너 정보 조회 실패 시 기본값 반환")
    void buildDeathEvent_WhenInspectFails_ShouldReturnDefaultEvent() {
        // given
        String containerId = "nonexistent123";

        when(dockerClient.inspectContainerCmd(anyString())).thenReturn(inspectContainerCmd);
        when(inspectContainerCmd.exec()).thenThrow(new RuntimeException("Container not found"));

        // when
        ContainerDeathEvent event = dockerService.buildDeathEvent(containerId, "die");

        // then
        assertThat(event).isNotNull();
        assertThat(event.getContainerId()).isEqualTo(containerId);
        assertThat(event.getContainerName()).isEqualTo("Unknown");
        assertThat(event.getDeathTime()).isNotNull();
        assertThat(event.getLastLogs()).contains("로그 조회 실패");
    }

    @Test
    @DisplayName("컨테이너 이름에서 슬래시 제거")
    void buildDeathEvent_ShouldRemoveLeadingSlashFromName() {
        // given
        when(dockerClient.inspectContainerCmd(anyString())).thenReturn(inspectContainerCmd);
        when(inspectContainerCmd.exec()).thenReturn(inspectContainerResponse);
        when(inspectContainerResponse.getState()).thenReturn(containerState);
        when(inspectContainerResponse.getName()).thenReturn("/prefix/my-app");
        when(inspectContainerResponse.getConfig()).thenReturn(containerConfig);
        when(containerConfig.getImage()).thenReturn("app:latest");
        when(containerState.getExitCodeLong()).thenReturn(0L);
        when(containerState.getOOMKilled()).thenReturn(false);
        when(containerState.getFinishedAt()).thenReturn(null);

        when(dockerClient.logContainerCmd(anyString())).thenReturn(logContainerCmd);
        when(logContainerCmd.withStdOut(true)).thenReturn(logContainerCmd);
        when(logContainerCmd.withStdErr(true)).thenReturn(logContainerCmd);
        when(logContainerCmd.withTail(anyInt())).thenReturn(logContainerCmd);
        when(logContainerCmd.withTimestamps(true)).thenReturn(logContainerCmd);

        // when
        ContainerDeathEvent event = dockerService.buildDeathEvent("test123", "die");

        // then
        assertThat(event.getContainerName()).isEqualTo("prefix/my-app");
    }

    @Test
    @DisplayName("종료 시간이 없을 때 현재 시간 사용")
    void buildDeathEvent_WhenFinishedAtNull_ShouldUseCurrentTime() {
        // given
        when(dockerClient.inspectContainerCmd(anyString())).thenReturn(inspectContainerCmd);
        when(inspectContainerCmd.exec()).thenReturn(inspectContainerResponse);
        when(inspectContainerResponse.getState()).thenReturn(containerState);
        when(inspectContainerResponse.getName()).thenReturn("/test-app");
        when(inspectContainerResponse.getConfig()).thenReturn(containerConfig);
        when(containerConfig.getImage()).thenReturn("test:latest");
        when(containerState.getExitCodeLong()).thenReturn(1L);
        when(containerState.getOOMKilled()).thenReturn(false);
        when(containerState.getFinishedAt()).thenReturn(null);

        when(dockerClient.logContainerCmd(anyString())).thenReturn(logContainerCmd);
        when(logContainerCmd.withStdOut(true)).thenReturn(logContainerCmd);
        when(logContainerCmd.withStdErr(true)).thenReturn(logContainerCmd);
        when(logContainerCmd.withTail(anyInt())).thenReturn(logContainerCmd);
        when(logContainerCmd.withTimestamps(true)).thenReturn(logContainerCmd);

        // when
        ContainerDeathEvent event = dockerService.buildDeathEvent("test123", "die");

        // then
        assertThat(event.getDeathTime()).isNotNull();
    }

    @Test
    @DisplayName("Docker 기본 시간값 처리")
    void buildDeathEvent_WhenFinishedAtDefault_ShouldUseCurrentTime() {
        // given
        when(dockerClient.inspectContainerCmd(anyString())).thenReturn(inspectContainerCmd);
        when(inspectContainerCmd.exec()).thenReturn(inspectContainerResponse);
        when(inspectContainerResponse.getState()).thenReturn(containerState);
        when(inspectContainerResponse.getName()).thenReturn("/test-app");
        when(inspectContainerResponse.getConfig()).thenReturn(containerConfig);
        when(containerConfig.getImage()).thenReturn("test:latest");
        when(containerState.getExitCodeLong()).thenReturn(1L);
        when(containerState.getOOMKilled()).thenReturn(false);
        when(containerState.getFinishedAt()).thenReturn("0001-01-01T00:00:00Z");

        when(dockerClient.logContainerCmd(anyString())).thenReturn(logContainerCmd);
        when(logContainerCmd.withStdOut(true)).thenReturn(logContainerCmd);
        when(logContainerCmd.withStdErr(true)).thenReturn(logContainerCmd);
        when(logContainerCmd.withTail(anyInt())).thenReturn(logContainerCmd);
        when(logContainerCmd.withTimestamps(true)).thenReturn(logContainerCmd);

        // when
        ContainerDeathEvent event = dockerService.buildDeathEvent("test123", "die");

        // then
        assertThat(event.getDeathTime()).isNotNull();
        assertThat(event.getDeathTime().getYear()).isGreaterThan(2000);
    }

    @Test
    @DisplayName("컨테이너 재시작 - 성공")
    void restartContainer_ShouldReturnTrueOnSuccess() {
        // given
        String containerId = "abc123def456";
        when(dockerClient.startContainerCmd(containerId)).thenReturn(startContainerCmd);

        // when
        boolean result = dockerService.restartContainer(containerId);

        // then
        assertThat(result).isTrue();
        verify(dockerClient).startContainerCmd(containerId);
        verify(startContainerCmd).exec();
    }

    @Test
    @DisplayName("컨테이너 재시작 - 실패")
    void restartContainer_ShouldReturnFalseOnFailure() {
        // given
        String containerId = "nonexistent123";
        when(dockerClient.startContainerCmd(containerId)).thenReturn(startContainerCmd);
        doThrow(new RuntimeException("Container not found")).when(startContainerCmd).exec();

        // when
        boolean result = dockerService.restartContainer(containerId);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("컨테이너 강제 정지 - 성공")
    void stopContainer_ShouldCallDockerStopCmd() {
        // given
        String containerId = "abc123def456";
        when(dockerClient.stopContainerCmd(containerId)).thenReturn(stopContainerCmd);

        // when
        boolean result = dockerService.stopContainer(containerId);

        // then
        assertThat(result).isTrue();
        verify(dockerClient).stopContainerCmd(containerId);
        verify(stopContainerCmd).exec();
    }

    @Test
    @DisplayName("컨테이너 강제 정지 - 실패 시 false 반환")
    void stopContainer_ShouldReturnFalseOnFailure() {
        // given
        String containerId = "nonexistent123";
        when(dockerClient.stopContainerCmd(containerId)).thenReturn(stopContainerCmd);
        doThrow(new RuntimeException("Container not found")).when(stopContainerCmd).exec();

        // when
        boolean result = dockerService.stopContainer(containerId);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("nodeId로 컨테이너 강제 정지 - nodeId null이면 로컬 사용")
    void stopContainer_WithNullNodeId_ShouldUseLocalClient() {
        // given
        String containerId = "abc123def456";
        when(dockerClient.stopContainerCmd(containerId)).thenReturn(stopContainerCmd);

        // when
        boolean result = dockerService.stopContainer(containerId, (String) null);

        // then
        assertThat(result).isTrue();
        verify(dockerClient).stopContainerCmd(containerId);
    }

    @Test
    @DisplayName("노드별 DockerClient로 컨테이너 재시작")
    void restartContainer_WithExplicitDockerClient_ShouldUseProvidedClient() {
        // given
        DockerClient remoteClient = mock(DockerClient.class);
        StartContainerCmd remoteStartCmd = mock(StartContainerCmd.class);
        String containerId = "remote-c123";

        when(remoteClient.startContainerCmd(containerId)).thenReturn(remoteStartCmd);

        // when
        boolean result = dockerService.restartContainer(containerId, remoteClient);

        // then
        assertThat(result).isTrue();
        verify(remoteClient).startContainerCmd(containerId);
        verify(dockerClient, never()).startContainerCmd(anyString());
    }
}
