package com.lite_k8s.metrics;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TimelineEntry {
    private String containerId;
    private String containerName;
    private String summary;
    private String status;
    private LocalDateTime occurredAt;
}
