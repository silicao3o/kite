package com.lite_k8s.incident;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class IncidentReport {

    public enum Status {
        OPEN, ANALYZING, CLOSED
    }

    private String id;
    private String containerId;
    private String containerName;
    private String summary;
    private String rootCause;
    private List<String> timeline;
    private List<String> suggestions;
    private Status status;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;

    @Builder
    public IncidentReport(String containerId, String containerName, String summary,
                          String rootCause, List<String> timeline, List<String> suggestions) {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.status = Status.OPEN;
        this.containerId = containerId;
        this.containerName = containerName;
        this.summary = summary;
        this.rootCause = rootCause;
        this.timeline = timeline != null ? timeline : new ArrayList<>();
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
    }
}
