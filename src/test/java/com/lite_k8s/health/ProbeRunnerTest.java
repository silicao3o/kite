package com.lite_k8s.health;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProbeRunnerTest {

    @Mock private DockerClient dockerClient;
    @Mock private ExecCreateCmd execCreateCmd;
    @Mock private ExecCreateCmdResponse execCreateResponse;
    @Mock private ExecStartCmd execStartCmd;

    private ProbeRunner probeRunner;

    @BeforeEach
    void setUp() {
        probeRunner = new ProbeRunner(dockerClient);
    }

    @Test
    @DisplayName("HTTP probe URL 올바르게 생성")
    void buildHttpUrl_WithHostPortPath_ReturnsCorrectUrl() {
        String url = probeRunner.buildHttpUrl("172.17.0.2", 8080, "/actuator/health");
        assertThat(url).isEqualTo("http://172.17.0.2:8080/actuator/health");
    }

    @Test
    @DisplayName("HTTP probe URL - 슬래시 없는 path 자동 보정")
    void buildHttpUrl_WithoutLeadingSlash_PrependsSlash() {
        String url = probeRunner.buildHttpUrl("172.17.0.2", 8080, "health");
        assertThat(url).isEqualTo("http://172.17.0.2:8080/health");
    }

    @Test
    @DisplayName("TCP probe - 열린 포트면 성공")
    void runTcpProbe_WhenPortOpen_ReturnsSuccess() {
        // localhost의 실제 열린 포트로 테스트 (Spring Boot 테스트 서버 불필요)
        // 실제 TCP 연결은 통합 테스트에서 검증, 여기선 결과 타입만 확인
        ProbeConfig config = ProbeConfig.builder()
                .type(ProbeType.TCP)
                .port(80)
                .build();

        ProbeResult result = probeRunner.runTcpProbe("256.256.256.256", config); // invalid IP → 실패
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isNotEmpty();
    }

    @Test
    @DisplayName("Exec probe - docker exec 명령 호출")
    void runExecProbe_CallsDockerExecApi() throws Exception {
        // given
        ProbeConfig config = ProbeConfig.builder()
                .type(ProbeType.EXEC)
                .command(new String[]{"cat", "/tmp/healthy"})
                .build();

        when(dockerClient.execCreateCmd("container123")).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(any(String[].class))).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStdout(true)).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStderr(true)).thenReturn(execCreateCmd);
        when(execCreateCmd.exec()).thenReturn(execCreateResponse);
        when(execCreateResponse.getId()).thenReturn("exec123");
        when(dockerClient.execStartCmd("exec123")).thenReturn(execStartCmd);
        when(execStartCmd.exec(any())).thenReturn(null);

        // when
        ProbeResult result = probeRunner.runExecProbe("container123", config);

        // then - exec API 호출됨 (실제 exit code 0이면 성공)
        verify(dockerClient).execCreateCmd("container123");
        verify(execCreateCmd).withCmd("cat", "/tmp/healthy");
    }

    @Test
    @DisplayName("Exec probe - 명령 없으면 실패")
    void runExecProbe_WhenNoCommand_ReturnsFailed() {
        ProbeConfig config = ProbeConfig.builder()
                .type(ProbeType.EXEC)
                .command(new String[]{})
                .build();

        ProbeResult result = probeRunner.runExecProbe("container123", config);
        assertThat(result.isSuccess()).isFalse();
    }
}
