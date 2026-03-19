package com.lite_k8s.ai;

import com.lite_k8s.model.ContainerDeathEvent;
import com.lite_k8s.model.ContainerMetrics;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 이상 컨텍스트 빌더
 *
 * 이벤트, 메트릭, 로그 등을 조합하여 AnomalyContext 생성
 */
@Component
public class AnomalyContextBuilder {

    private String containerName;
    private String containerId;
    private String imageName;
    private String eventType;
    private int exitCode;
    private boolean oomKilled;
    private String recentLogs;
    private double cpuPercent;
    private double memoryPercent;
    private long memoryUsage;
    private long memoryLimit;
    private long networkRxBytes;
    private long networkTxBytes;
    private int restartCount;
    private Map<String, String> labels;

    /**
     * 이벤트에서 기본 정보 추출
     */
    public AnomalyContextBuilder fromEvent(ContainerDeathEvent event) {
        this.containerName = event.getContainerName();
        this.containerId = event.getContainerId();
        this.imageName = event.getImageName();
        this.eventType = event.getAction();
        this.exitCode = event.getExitCode() != null ? event.getExitCode().intValue() : 0;
        this.oomKilled = event.isOomKilled();
        this.labels = event.getLabels();

        // 이벤트에 로그가 포함되어 있으면 사용
        if (event.getLastLogs() != null) {
            this.recentLogs = event.getLastLogs();
        }

        return this;
    }

    /**
     * 메트릭 추가
     */
    public AnomalyContextBuilder withMetrics(ContainerMetrics metrics) {
        if (metrics != null) {
            this.cpuPercent = metrics.getCpuPercent();
            this.memoryPercent = metrics.getMemoryPercent();
            this.memoryUsage = metrics.getMemoryUsage();
            this.memoryLimit = metrics.getMemoryLimit();
            this.networkRxBytes = metrics.getNetworkRxBytes();
            this.networkTxBytes = metrics.getNetworkTxBytes();
        }
        return this;
    }

    /**
     * 로그 추가
     */
    public AnomalyContextBuilder withLogs(String logs) {
        this.recentLogs = logs;
        return this;
    }

    /**
     * 재시작 횟수 추가
     */
    public AnomalyContextBuilder withRestartCount(int count) {
        this.restartCount = count;
        return this;
    }

    /**
     * 라벨 추가
     */
    public AnomalyContextBuilder withLabels(Map<String, String> labels) {
        this.labels = labels;
        return this;
    }

    /**
     * AnomalyContext 생성
     */
    public AnomalyContext build() {
        return AnomalyContext.builder()
                .containerName(containerName)
                .containerId(containerId)
                .imageName(imageName)
                .eventType(eventType)
                .exitCode(exitCode)
                .oomKilled(oomKilled)
                .recentLogs(recentLogs)
                .cpuPercent(cpuPercent)
                .memoryPercent(memoryPercent)
                .memoryUsage(memoryUsage)
                .memoryLimit(memoryLimit)
                .networkRxBytes(networkRxBytes)
                .networkTxBytes(networkTxBytes)
                .restartCount(restartCount)
                .labels(labels)
                .build();
    }

    /**
     * 빌더 초기화
     */
    public AnomalyContextBuilder reset() {
        this.containerName = null;
        this.containerId = null;
        this.imageName = null;
        this.eventType = null;
        this.exitCode = 0;
        this.oomKilled = false;
        this.recentLogs = null;
        this.cpuPercent = 0;
        this.memoryPercent = 0;
        this.memoryUsage = 0;
        this.memoryLimit = 0;
        this.networkRxBytes = 0;
        this.networkTxBytes = 0;
        this.restartCount = 0;
        this.labels = null;
        return this;
    }
}
