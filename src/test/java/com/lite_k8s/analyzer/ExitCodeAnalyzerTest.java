package com.lite_k8s.analyzer;

import com.lite_k8s.model.ContainerDeathEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ExitCodeAnalyzerTest {

    private ExitCodeAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new ExitCodeAnalyzer();
    }

    @Test
    @DisplayName("OOM Killed 이벤트 분석")
    void analyze_WhenOomKilled_ShouldReturnOomMessage() {
        // given
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerId("abc123")
                .containerName("test-container")
                .exitCode(137L)
                .oomKilled(true)
                .action("oom")
                .deathTime(LocalDateTime.now())
                .build();

        // when
        String result = analyzer.analyze(event);

        // then
        assertThat(result).contains("OOM Killed");
        assertThat(result).contains("메모리 부족");
        assertThat(result).contains("메모리 제한 증가");
    }

    @Test
    @DisplayName("정상 종료 (Exit Code 0) 분석")
    void analyze_WhenExitCodeZero_ShouldReturnNormalExit() {
        // given
        ContainerDeathEvent event = createEventWithExitCode(0L);

        // when
        String result = analyzer.analyze(event);

        // then
        assertThat(result).contains("Exit Code: 0");
        assertThat(result).contains("정상 종료");
    }

    @Test
    @DisplayName("SIGKILL (Exit Code 137) 분석")
    void analyze_WhenExitCode137_ShouldReturnSigkillMessage() {
        // given
        ContainerDeathEvent event = createEventWithExitCode(137L);

        // when
        String result = analyzer.analyze(event);

        // then
        assertThat(result).contains("Exit Code: 137");
        assertThat(result).contains("SIGKILL");
        assertThat(result).contains("강제 종료");
    }

    @Test
    @DisplayName("SIGTERM (Exit Code 143) 분석")
    void analyze_WhenExitCode143_ShouldReturnSigtermMessage() {
        // given
        ContainerDeathEvent event = createEventWithExitCode(143L);

        // when
        String result = analyzer.analyze(event);

        // then
        assertThat(result).contains("Exit Code: 143");
        assertThat(result).contains("SIGTERM");
        assertThat(result).contains("docker stop");
    }

    @Test
    @DisplayName("일반 에러 (Exit Code 1) 분석")
    void analyze_WhenExitCode1_ShouldReturnGeneralError() {
        // given
        ContainerDeathEvent event = createEventWithExitCode(1L);

        // when
        String result = analyzer.analyze(event);

        // then
        assertThat(result).contains("Exit Code: 1");
        assertThat(result).contains("일반 에러");
        assertThat(result).contains("애플리케이션");
    }

    @Test
    @DisplayName("세그멘테이션 폴트 (Exit Code 139) 분석")
    void analyze_WhenExitCode139_ShouldReturnSegfaultMessage() {
        // given
        ContainerDeathEvent event = createEventWithExitCode(139L);

        // when
        String result = analyzer.analyze(event);

        // then
        assertThat(result).contains("Exit Code: 139");
        assertThat(result).contains("SIGSEGV");
        assertThat(result).contains("세그멘테이션 폴트");
    }

    @ParameterizedTest
    @DisplayName("다양한 Exit Code 분석")
    @CsvSource({
            "126, 명령 실행 불가",
            "127, 명령을 찾을 수 없음",
            "130, SIGINT"
    })
    void analyze_WithVariousExitCodes_ShouldReturnCorrectMessage(Long exitCode, String expectedKeyword) {
        // given
        ContainerDeathEvent event = createEventWithExitCode(exitCode);

        // when
        String result = analyzer.analyze(event);

        // then
        assertThat(result).contains("Exit Code: " + exitCode);
        assertThat(result).contains(expectedKeyword);
    }

    @Test
    @DisplayName("알 수 없는 Exit Code 분석")
    void analyze_WhenUnknownExitCode_ShouldReturnUnknownMessage() {
        // given
        ContainerDeathEvent event = createEventWithExitCode(999L);

        // when
        String result = analyzer.analyze(event);

        // then
        assertThat(result).contains("Exit Code: 999");
        assertThat(result).contains("알 수 없는 종료 코드");
    }

    @Test
    @DisplayName("Exit Code가 null일 때 분석")
    void analyze_WhenExitCodeNull_ShouldReturnUnknownMessage() {
        // given
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerId("abc123")
                .containerName("test-container")
                .exitCode(null)
                .oomKilled(false)
                .action("die")
                .deathTime(LocalDateTime.now())
                .build();

        // when
        String result = analyzer.analyze(event);

        // then
        assertThat(result).contains("확인할 수 없음");
    }

    @Test
    @DisplayName("kill 액션으로 종료된 경우 추가 정보 포함")
    void analyze_WhenKillAction_ShouldIncludeKillInfo() {
        // given
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerId("abc123")
                .containerName("test-container")
                .exitCode(137L)
                .oomKilled(false)
                .action("kill")
                .deathTime(LocalDateTime.now())
                .build();

        // when
        String result = analyzer.analyze(event);

        // then
        assertThat(result).contains("kill 이벤트");
        assertThat(result).contains("외부에서 강제 종료");
    }

    private ContainerDeathEvent createEventWithExitCode(Long exitCode) {
        return ContainerDeathEvent.builder()
                .containerId("abc123")
                .containerName("test-container")
                .exitCode(exitCode)
                .oomKilled(false)
                .action("die")
                .deathTime(LocalDateTime.now())
                .build();
    }
}
