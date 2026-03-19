package com.lite_k8s.incident;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class Suggestion {

    public enum Type {
        CONFIG_OPTIMIZATION, PLAYBOOK_IMPROVEMENT, GENERAL
    }

    public enum Status {
        PENDING, APPROVED, REJECTED
    }

    private String id;
    private String containerName;
    private Type type;
    private String content;
    private Status status;
    private LocalDateTime createdAt;
    private int patternOccurrenceCount;

    @Builder
    public Suggestion(String containerName, Type type, String content, int patternOccurrenceCount) {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.status = Status.PENDING;
        this.containerName = containerName;
        this.type = type;
        this.content = content;
        this.patternOccurrenceCount = patternOccurrenceCount;
    }
}
