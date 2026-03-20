package com.lite_k8s.incident;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "incident_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentReport {

    public enum Status {
        OPEN, ANALYZING, CLOSED
    }

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String containerId;
    private String containerName;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String rootCause;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "incident_timeline", joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "entry", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> timeline = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "incident_suggestions", joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "suggestion", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> suggestions = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.OPEN;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime closedAt;
}
