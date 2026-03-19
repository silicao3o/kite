package com.lite_k8s.service;

import com.lite_k8s.model.LogEntry;
import com.lite_k8s.model.LogSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogSearchService {

    private final DockerService dockerService;

    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.?\\d*Z?)\\s+(\\w+)?\\s*(.*)$"
    );

    private static final List<String> LOG_LEVELS = List.of("TRACE", "DEBUG", "INFO", "WARN", "WARNING", "ERROR", "FATAL");

    public LogSearchResult search(String containerId, String keyword,
                                   LocalDateTime fromTime, LocalDateTime toTime,
                                   List<String> levels) {
        String rawLogs = dockerService.getContainerLogs(containerId);
        List<LogEntry> entries = parseAndFilter(rawLogs, keyword, fromTime, toTime, levels);

        return LogSearchResult.builder()
                .entries(entries)
                .totalCount(entries.size())
                .keyword(keyword)
                .fromTime(fromTime)
                .toTime(toTime)
                .levels(levels)
                .build();
    }

    private List<LogEntry> parseAndFilter(String rawLogs, String keyword,
                                           LocalDateTime fromTime, LocalDateTime toTime,
                                           List<String> levels) {
        if (rawLogs == null || rawLogs.isEmpty()) {
            return List.of();
        }

        List<LogEntry> result = new ArrayList<>();
        String[] lines = rawLogs.split("\n");
        int lineNumber = 0;

        for (String line : lines) {
            lineNumber++;
            if (line.trim().isEmpty()) {
                continue;
            }

            LogEntry entry = parseLine(line, lineNumber);
            if (entry == null) {
                continue;
            }

            // 필터 적용
            if (!matchesFilters(entry, keyword, fromTime, toTime, levels)) {
                continue;
            }

            // 키워드 하이라이트
            if (keyword != null && !keyword.isEmpty()) {
                entry.setHighlightedMessage(highlightKeyword(entry.getMessage(), keyword));
            } else {
                entry.setHighlightedMessage(entry.getMessage());
            }

            result.add(entry);
        }

        return result;
    }

    private LogEntry parseLine(String line, int lineNumber) {
        Matcher matcher = LOG_PATTERN.matcher(line);

        LocalDateTime timestamp = null;
        String level = "INFO";
        String message = line;

        if (matcher.matches()) {
            String timestampStr = matcher.group(1);
            String possibleLevel = matcher.group(2);
            String content = matcher.group(3);

            timestamp = parseTimestamp(timestampStr);

            // 레벨 판별
            if (possibleLevel != null && LOG_LEVELS.contains(possibleLevel.toUpperCase())) {
                level = possibleLevel.toUpperCase();
                message = possibleLevel + " " + (content != null ? content : "");
            } else {
                message = line.substring(line.indexOf('Z') + 1).trim();
                if (message.isEmpty()) {
                    message = line;
                }
                // 메시지에서 레벨 추출 시도
                level = extractLevelFromMessage(message);
            }
        } else {
            // 타임스탬프 없는 라인 - 레벨만 추출 시도
            level = extractLevelFromMessage(line);
        }

        return LogEntry.builder()
                .timestamp(timestamp)
                .level(level)
                .message(message)
                .lineNumber(lineNumber)
                .build();
    }

    private LocalDateTime parseTimestamp(String timestampStr) {
        try {
            if (timestampStr.endsWith("Z")) {
                return LocalDateTime.parse(
                        timestampStr.substring(0, Math.min(23, timestampStr.length() - 1)),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]")
                );
            }
            return LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String extractLevelFromMessage(String message) {
        String upperMessage = message.toUpperCase();
        for (String level : LOG_LEVELS) {
            if (upperMessage.contains(level)) {
                return level.equals("WARNING") ? "WARN" : level;
            }
        }
        return "INFO";
    }

    private boolean matchesFilters(LogEntry entry, String keyword,
                                    LocalDateTime fromTime, LocalDateTime toTime,
                                    List<String> levels) {
        // 키워드 필터
        if (keyword != null && !keyword.isEmpty()) {
            if (!entry.getMessage().toLowerCase().contains(keyword.toLowerCase())) {
                return false;
            }
        }

        // 시간 범위 필터
        if (entry.getTimestamp() != null) {
            if (fromTime != null && entry.getTimestamp().isBefore(fromTime)) {
                return false;
            }
            if (toTime != null && entry.getTimestamp().isAfter(toTime)) {
                return false;
            }
        }

        // 레벨 필터
        if (levels != null && !levels.isEmpty()) {
            List<String> upperLevels = levels.stream()
                    .map(String::toUpperCase)
                    .toList();
            if (!upperLevels.contains(entry.getLevel())) {
                return false;
            }
        }

        return true;
    }

    private String highlightKeyword(String message, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return message;
        }

        // 대소문자 구분 없이 키워드를 찾아서 하이라이트
        Pattern pattern = Pattern.compile("(" + Pattern.quote(keyword) + ")", Pattern.CASE_INSENSITIVE);
        return pattern.matcher(message).replaceAll("<mark>$1</mark>");
    }
}
