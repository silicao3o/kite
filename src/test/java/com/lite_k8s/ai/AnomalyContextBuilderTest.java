package com.lite_k8s.ai;

import com.lite_k8s.model.ContainerDeathEvent;
import com.lite_k8s.model.ContainerMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyContextBuilderTest {

    private AnomalyContextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new AnomalyContextBuilder();
    }

    @Test
    @DisplayName("이벤트에서 컨텍스트 생성")
    void shouldBuildFromEvent() {
        // given
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerName("web-server")
                .containerId("abc123def456")
                .imageName("nginx:latest")
                .action("die")
                .exitCode(137L)
                .oomKilled(true)
                .deathTime(LocalDateTime.now())
                .build();

        // when
        AnomalyContext context = builder.fromEvent(event).build();

        // then
        assertThat(context.getContainerName()).isEqualTo("web-server");
        assertThat(context.getContainerId()).isEqualTo("abc123def456");
        assertThat(context.getEventType()).isEqualTo("die");
        assertThat(context.getExitCode()).isEqualTo(137);
        assertThat(context.isOomKilled()).isTrue();
    }

    @Test
    @DisplayName("메트릭 추가")
    void shouldAddMetrics() {
        // given
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerName("web")
                .containerId("123")
                .action("die")
                .build();

        ContainerMetrics metrics = ContainerMetrics.builder()
                .cpuPercent(85.5)
                .memoryPercent(92.3)
                .memoryUsage(1024 * 1024 * 500)  // 500MB
                .memoryLimit(1024 * 1024 * 1024)  // 1GB
                .networkRxBytes(1000000)
                .networkTxBytes(500000)
                .build();

        // when
        AnomalyContext context = builder
                .fromEvent(event)
                .withMetrics(metrics)
                .build();

        // then
        assertThat(context.getCpuPercent()).isEqualTo(85.5);
        assertThat(context.getMemoryPercent()).isEqualTo(92.3);
        assertThat(context.getMemoryUsage()).isEqualTo(1024 * 1024 * 500);
    }

    @Test
    @DisplayName("로그 추가")
    void shouldAddLogs() {
        // given
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerName("web")
                .containerId("123")
                .action("die")
                .build();

        String logs = "ERROR: Connection refused\nFATAL: Shutting down";

        // when
        AnomalyContext context = builder
                .fromEvent(event)
                .withLogs(logs)
                .build();

        // then
        assertThat(context.getRecentLogs()).contains("ERROR");
        assertThat(context.getRecentLogs()).contains("FATAL");
    }

    @Test
    @DisplayName("재시작 횟수 추가")
    void shouldAddRestartCount() {
        // given
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerName("web")
                .containerId("123")
                .action("die")
                .build();

        // when
        AnomalyContext context = builder
                .fromEvent(event)
                .withRestartCount(3)
                .build();

        // then
        assertThat(context.getRestartCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("라벨 추가")
    void shouldAddLabels() {
        // given
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerName("web")
                .containerId("123")
                .action("die")
                .labels(Map.of("app", "web-server", "env", "production"))
                .build();

        // when
        AnomalyContext context = builder
                .fromEvent(event)
                .build();

        // then
        assertThat(context.getLabels()).containsEntry("app", "web-server");
        assertThat(context.getLabels()).containsEntry("env", "production");
    }

    @Test
    @DisplayName("전체 컨텍스트 조합")
    void shouldBuildCompleteContext() {
        // given
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerName("api-server")
                .containerId("abc123")
                .imageName("my-api:v1.2.3")
                .action("oom")
                .exitCode(137L)
                .oomKilled(true)
                .deathTime(LocalDateTime.now())
                .labels(Map.of("service", "api"))
                .build();

        ContainerMetrics metrics = ContainerMetrics.builder()
                .cpuPercent(45.0)
                .memoryPercent(99.5)
                .memoryUsage(1024 * 1024 * 900)
                .memoryLimit(1024 * 1024 * 1024)
                .build();

        String logs = "OutOfMemoryError: Java heap space";

        // when
        AnomalyContext context = builder
                .fromEvent(event)
                .withMetrics(metrics)
                .withLogs(logs)
                .withRestartCount(2)
                .build();

        // then
        assertThat(context.getContainerName()).isEqualTo("api-server");
        assertThat(context.getEventType()).isEqualTo("oom");
        assertThat(context.isOomKilled()).isTrue();
        assertThat(context.getCpuPercent()).isEqualTo(45.0);
        assertThat(context.getMemoryPercent()).isEqualTo(99.5);
        assertThat(context.getRecentLogs()).contains("OutOfMemoryError");
        assertThat(context.getRestartCount()).isEqualTo(2);
        assertThat(context.getLabels()).containsEntry("service", "api");
    }
}
