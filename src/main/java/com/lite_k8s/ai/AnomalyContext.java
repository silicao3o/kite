package com.lite_k8s.ai;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 이상 이벤트 컨텍스트
 *
 * AI에게 전달할 컨테이너 이상 상황 정보
 */
@Data
@Builder
public class AnomalyContext {

    // 컨테이너 기본 정보
    private String containerName;
    private String containerId;
    private String imageName;

    // 이벤트 정보
    private String eventType;      // die, kill, oom 등
    private int exitCode;
    private boolean oomKilled;
    private LocalDateTime eventTime;

    // 로그
    private String recentLogs;     // 최근 로그 (마지막 N줄)

    // 메트릭
    private double cpuPercent;
    private double memoryPercent;
    private long memoryUsage;
    private long memoryLimit;
    private long networkRxBytes;
    private long networkTxBytes;

    // 이력
    private int restartCount;      // 최근 재시작 횟수
    private int recentFailures;    // 최근 실패 횟수

    // 라벨
    private Map<String, String> labels;

    /**
     * 요약 문자열 생성
     */
    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Container: ").append(containerName).append("\n");
        sb.append("Event: ").append(eventType).append("\n");
        sb.append("Exit Code: ").append(exitCode).append("\n");
        sb.append("OOM Killed: ").append(oomKilled ? "Yes" : "No").append("\n");
        sb.append("CPU: ").append(String.format("%.1f%%", cpuPercent)).append("\n");
        sb.append("Memory: ").append(String.format("%.1f%%", memoryPercent)).append("\n");
        sb.append("Restart Count: ").append(restartCount).append("\n");
        return sb.toString();
    }
}
