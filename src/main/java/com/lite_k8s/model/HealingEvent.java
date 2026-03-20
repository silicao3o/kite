package com.lite_k8s.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "healing_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealingEvent {
    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();
    private String containerId;
    private String containerName;
    private LocalDateTime timestamp;
    private boolean success;
    private int restartCount;
    private String message;
}
