package com.lite_k8s.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class LogSearchResult {
    private List<LogEntry> entries;
    private int totalCount;
    private String keyword;
    private LocalDateTime fromTime;
    private LocalDateTime toTime;
    private List<String> levels;
}
