package com.lite_k8s.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ContainerInfo {
    private String id;
    private String shortId;
    private String name;
    private String image;
    private String status;
    private String state;
    private LocalDateTime created;
    private List<PortMapping> ports;
    private Map<String, String> labels;

    // 자가치유 관련 필드
    private boolean healingEnabled;
    private int restartCount;
    private int maxRestarts;

    // 메트릭 필드
    private double cpuPercent;
    private long memoryUsage;
    private long memoryLimit;
    private double memoryPercent;
    private long networkRxBytes;
    private long networkTxBytes;

    public String getMemoryDisplay() {
        if (memoryLimit <= 0) return "-";
        return formatBytes(memoryUsage) + " / " + formatBytes(memoryLimit);
    }

    public String getCpuDisplay() {
        return String.format("%.1f%%", cpuPercent);
    }

    public String getMemoryPercentDisplay() {
        return String.format("%.1f%%", memoryPercent);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public String getStatusClass() {
        if (state == null) return "unknown";
        return switch (state.toLowerCase()) {
            case "running" -> "running";
            case "exited", "dead" -> "exited";
            case "paused" -> "paused";
            case "restarting" -> "restarting";
            case "created" -> "created";
            default -> "unknown";
        };
    }

    @Data
    @Builder
    public static class PortMapping {
        private int privatePort;
        private int publicPort;
        private String type;

        public String getDisplayString() {
            if (publicPort > 0) {
                return publicPort + ":" + privatePort + "/" + type;
            }
            return privatePort + "/" + type;
        }
    }
}
