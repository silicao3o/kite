package com.lite_k8s.incident;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "suggestions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Suggestion {

    public enum Type {
        CONFIG_OPTIMIZATION, PLAYBOOK_IMPROVEMENT, GENERAL
    }

    public enum Status {
        PENDING, APPROVED, REJECTED
    }

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String containerName;

    @Enumerated(EnumType.STRING)
    private Type type;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.PENDING;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private int patternOccurrenceCount;
}
