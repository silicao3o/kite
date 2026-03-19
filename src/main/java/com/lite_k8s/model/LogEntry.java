package com.lite_k8s.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class LogEntry {
    private LocalDateTime timestamp;
    private String level;
    private String message;
    private String highlightedMessage;
    private int lineNumber;
}
