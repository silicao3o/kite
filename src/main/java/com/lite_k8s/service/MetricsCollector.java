package com.lite_k8s.service;

import com.lite_k8s.model.ContainerMetrics;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.CpuStatsConfig;
import com.github.dockerjava.api.model.MemoryStatsConfig;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.api.model.StatisticNetworksConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsCollector {

    private final DockerClient dockerClient;

    public Optional<ContainerMetrics> collectMetrics(String containerId, String containerName) {
        try {
            AtomicReference<Statistics> statsRef = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            dockerClient.statsCmd(containerId)
                    .withNoStream(true)
                    .exec(new ResultCallback.Adapter<Statistics>() {
                        @Override
                        public void onNext(Statistics stats) {
                            statsRef.set(stats);
                            latch.countDown();
                        }

                        @Override
                        public void onComplete() {
                            latch.countDown();
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            log.error("Stats 수집 에러: {}", containerId, throwable);
                            latch.countDown();
                        }
                    });

            if (!latch.await(5, TimeUnit.SECONDS)) {
                log.warn("Stats 수집 타임아웃: {}", containerId);
                return Optional.empty();
            }

            Statistics stats = statsRef.get();
            if (stats == null) {
                return Optional.empty();
            }

            return Optional.of(buildMetrics(containerId, containerName, stats));

        } catch (Exception e) {
            log.error("메트릭 수집 실패: {}", containerId, e);
            return Optional.empty();
        }
    }

    private ContainerMetrics buildMetrics(String containerId, String containerName, Statistics stats) {
        double cpuPercent = calculateCpuPercent(stats);
        long memoryUsage = getMemoryUsage(stats);
        long memoryLimit = getMemoryLimit(stats);
        double memoryPercent = calculateMemoryPercent(memoryUsage, memoryLimit);

        long networkRx = 0;
        long networkTx = 0;
        Map<String, StatisticNetworksConfig> networks = stats.getNetworks();
        if (networks != null) {
            for (StatisticNetworksConfig network : networks.values()) {
                networkRx += network.getRxBytes();
                networkTx += network.getTxBytes();
            }
        }

        return ContainerMetrics.builder()
                .containerId(containerId)
                .containerName(containerName)
                .cpuPercent(cpuPercent)
                .memoryUsage(memoryUsage)
                .memoryLimit(memoryLimit)
                .memoryPercent(memoryPercent)
                .networkRxBytes(networkRx)
                .networkTxBytes(networkTx)
                .collectedAt(LocalDateTime.now())
                .build();
    }

    private double calculateCpuPercent(Statistics stats) {
        CpuStatsConfig cpuStats = stats.getCpuStats();
        CpuStatsConfig preCpuStats = stats.getPreCpuStats();

        if (cpuStats == null || preCpuStats == null ||
            cpuStats.getCpuUsage() == null || preCpuStats.getCpuUsage() == null) {
            return 0.0;
        }

        long cpuDelta = cpuStats.getCpuUsage().getTotalUsage() -
                        preCpuStats.getCpuUsage().getTotalUsage();

        Long systemCpu = cpuStats.getSystemCpuUsage();
        Long preSystemCpu = preCpuStats.getSystemCpuUsage();

        if (systemCpu == null || preSystemCpu == null) {
            return 0.0;
        }

        long systemDelta = systemCpu - preSystemCpu;

        if (systemDelta <= 0 || cpuDelta < 0) {
            return 0.0;
        }

        Long onlineCpus = cpuStats.getOnlineCpus();
        int cpuCount = onlineCpus != null ? onlineCpus.intValue() : 1;

        return ((double) cpuDelta / systemDelta) * cpuCount * 100.0;
    }

    private long getMemoryUsage(Statistics stats) {
        MemoryStatsConfig memoryStats = stats.getMemoryStats();
        if (memoryStats == null || memoryStats.getUsage() == null) {
            return 0;
        }
        return memoryStats.getUsage();
    }

    private long getMemoryLimit(Statistics stats) {
        MemoryStatsConfig memoryStats = stats.getMemoryStats();
        if (memoryStats == null || memoryStats.getLimit() == null) {
            return 0;
        }
        return memoryStats.getLimit();
    }

    private double calculateMemoryPercent(long usage, long limit) {
        if (limit <= 0) {
            return 0.0;
        }
        return ((double) usage / limit) * 100.0;
    }
}
