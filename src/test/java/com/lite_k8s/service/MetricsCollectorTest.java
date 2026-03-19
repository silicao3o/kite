package com.lite_k8s.service;

import com.lite_k8s.model.ContainerMetrics;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsCollectorTest {

    @Mock
    private DockerClient dockerClient;

    @Mock
    private StatsCmd statsCmd;

    private MetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        metricsCollector = new MetricsCollector(dockerClient);
    }

    @Test
    @DisplayName("컨테이너 CPU 사용률을 수집할 수 있다")
    void shouldCollectCpuUsage() {
        // given
        String containerId = "abc123";
        Statistics stats = createMockStats(
                1000000000L, // cpu usage
                100000000L,  // previous cpu usage
                10000000000L, // system cpu usage
                1000000000L,  // previous system cpu usage
                4,            // cpu count
                100 * 1024 * 1024L, // memory usage
                512 * 1024 * 1024L  // memory limit
        );

        setupStatsMock(containerId, stats);

        // when
        Optional<ContainerMetrics> result = metricsCollector.collectMetrics(containerId, "test-container");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getCpuPercent()).isGreaterThan(0);
    }

    @Test
    @DisplayName("컨테이너 메모리 사용률을 수집할 수 있다")
    void shouldCollectMemoryUsage() {
        // given
        String containerId = "abc123";
        long memoryUsage = 100 * 1024 * 1024L; // 100MB
        long memoryLimit = 512 * 1024 * 1024L; // 512MB

        Statistics stats = createMockStats(
                1000000000L, 100000000L, 10000000000L, 1000000000L, 4,
                memoryUsage, memoryLimit
        );

        setupStatsMock(containerId, stats);

        // when
        Optional<ContainerMetrics> result = metricsCollector.collectMetrics(containerId, "test-container");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getMemoryUsage()).isEqualTo(memoryUsage);
        assertThat(result.get().getMemoryLimit()).isEqualTo(memoryLimit);
        assertThat(result.get().getMemoryPercent()).isCloseTo(19.53, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    @DisplayName("컨테이너 네트워크 I/O를 수집할 수 있다")
    void shouldCollectNetworkIO() {
        // given
        String containerId = "abc123";
        Statistics stats = createMockStatsWithNetwork(1000L, 2000L);

        setupStatsMock(containerId, stats);

        // when
        Optional<ContainerMetrics> result = metricsCollector.collectMetrics(containerId, "test-container");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getNetworkRxBytes()).isEqualTo(1000L);
        assertThat(result.get().getNetworkTxBytes()).isEqualTo(2000L);
    }

    @Test
    @DisplayName("stats 조회 실패 시 empty를 반환한다")
    void shouldReturnEmptyWhenStatsFails() {
        // given
        String containerId = "nonexistent";
        when(dockerClient.statsCmd(containerId)).thenThrow(new RuntimeException("Container not found"));

        // when
        Optional<ContainerMetrics> result = metricsCollector.collectMetrics(containerId, "test");

        // then
        assertThat(result).isEmpty();
    }

    private void setupStatsMock(String containerId, Statistics stats) {
        when(dockerClient.statsCmd(containerId)).thenReturn(statsCmd);
        when(statsCmd.withNoStream(true)).thenReturn(statsCmd);

        doAnswer(invocation -> {
            ResultCallback<Statistics> callback = invocation.getArgument(0);
            callback.onNext(stats);
            callback.onComplete();
            return null;
        }).when(statsCmd).exec(any());
    }

    private Statistics createMockStats(
            long cpuUsage, long preCpuUsage,
            long systemCpuUsage, long preSystemCpuUsage,
            int cpuCount,
            long memoryUsage, long memoryLimit) {

        Statistics stats = mock(Statistics.class);

        // CPU Stats
        CpuStatsConfig cpuStats = mock(CpuStatsConfig.class);
        CpuUsageConfig cpuUsageConfig = mock(CpuUsageConfig.class);
        when(cpuUsageConfig.getTotalUsage()).thenReturn(cpuUsage);
        when(cpuStats.getCpuUsage()).thenReturn(cpuUsageConfig);
        when(cpuStats.getSystemCpuUsage()).thenReturn(systemCpuUsage);
        when(cpuStats.getOnlineCpus()).thenReturn((long) cpuCount);
        when(stats.getCpuStats()).thenReturn(cpuStats);

        // Pre CPU Stats
        CpuStatsConfig preCpuStats = mock(CpuStatsConfig.class);
        CpuUsageConfig preCpuUsageConfig = mock(CpuUsageConfig.class);
        when(preCpuUsageConfig.getTotalUsage()).thenReturn(preCpuUsage);
        when(preCpuStats.getCpuUsage()).thenReturn(preCpuUsageConfig);
        when(preCpuStats.getSystemCpuUsage()).thenReturn(preSystemCpuUsage);
        when(stats.getPreCpuStats()).thenReturn(preCpuStats);

        // Memory Stats
        MemoryStatsConfig memoryStats = mock(MemoryStatsConfig.class);
        when(memoryStats.getUsage()).thenReturn(memoryUsage);
        when(memoryStats.getLimit()).thenReturn(memoryLimit);
        when(stats.getMemoryStats()).thenReturn(memoryStats);

        // Network Stats (empty by default)
        when(stats.getNetworks()).thenReturn(null);

        return stats;
    }

    private Statistics createMockStatsWithNetwork(long rxBytes, long txBytes) {
        Statistics stats = createMockStats(
                1000000000L, 100000000L, 10000000000L, 1000000000L, 4,
                100 * 1024 * 1024L, 512 * 1024 * 1024L
        );

        StatisticNetworksConfig networkConfig = mock(StatisticNetworksConfig.class);
        when(networkConfig.getRxBytes()).thenReturn(rxBytes);
        when(networkConfig.getTxBytes()).thenReturn(txBytes);
        when(stats.getNetworks()).thenReturn(Map.of("eth0", networkConfig));

        return stats;
    }
}
