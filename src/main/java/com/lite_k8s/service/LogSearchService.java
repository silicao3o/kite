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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogSearchService {

    private final DockerService dockerService;

    // 기존 단순 포맷: "2026-03-13T10:00:00.000Z INFO message"
    private static final Pattern SIMPLE_LOG_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.?\\d*Z?)\\s+(\\w+)?\\s*(.*)$"
    );

    // Spring Boot 포맷: "DockerTS AppTS LEVEL PID --- [app] [thread] logger : message"
    private static final Pattern SPRING_BOOT_LOG_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+Z)\\s+" +     // Docker timestamp
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+[+\\-]\\d{2}:\\d{2}\\s+" + // App timestamp
            "(\\w+)\\s+\\d+\\s+---\\s+" +                                       // Level + PID + ---
            "\\[[^\\]]+\\]\\s+" +                                                // [app-name]
            "\\[([^\\]]+)\\]\\s+" +                                              // [thread-name]
            "(\\S+)\\s+:\\s+" +                                                  // logger :
            "(.*)$"                                                              // message
    );

    private static final List<String> LOG_LEVELS = List.of("TRACE", "DEBUG", "INFO", "WARN", "WARNING", "ERROR", "FATAL");

    // 기존 API 호환용 오버로드
    public LogSearchResult search(String containerId, String keyword,
                                   LocalDateTime fromTime, LocalDateTime toTime,
                                   List<String> levels) {
        return search(containerId, keyword, fromTime, toTime, levels, null, null, null, null);
    }

    // 스레드/traceId 필터 추가 오버로드
    public LogSearchResult search(String containerId, String keyword,
                                   LocalDateTime fromTime, LocalDateTime toTime,
                                   List<String> levels, String threadName, String traceId) {
        return search(containerId, keyword, fromTime, toTime, levels, threadName, traceId, null, null);
    }

    // 전체 파라미터 버전 (멀티노드 포함)
    public LogSearchResult search(String containerId, String keyword,
                                   LocalDateTime fromTime, LocalDateTime toTime,
                                   List<String> levels, String threadName, String traceId,
                                   String containerName, String nodeId) {
        String rawLogs = dockerService.getContainerLogs(containerId, containerName, nodeId);
        List<LogEntry> allEntries = parseAll(rawLogs);

        // 사용 가능한 스레드 목록 수집 (필터 적용 전)
        Set<String> threadSet = new LinkedHashSet<>();
        for (LogEntry entry : allEntries) {
            if (entry.getThreadName() != null) {
                threadSet.add(entry.getThreadName());
            }
        }

        // 필터 적용
        List<LogEntry> filtered = new ArrayList<>();
        for (LogEntry entry : allEntries) {
            if (!matchesFilters(entry, keyword, fromTime, toTime, levels, threadName, traceId)) {
                continue;
            }

            // 키워드 하이라이트
            if (keyword != null && !keyword.isEmpty()) {
                entry.setHighlightedMessage(highlightKeyword(entry.getMessage(), keyword));
            } else {
                entry.setHighlightedMessage(entry.getMessage());
            }

            filtered.add(entry);
        }

        return LogSearchResult.builder()
                .entries(filtered)
                .totalCount(filtered.size())
                .keyword(keyword)
                .fromTime(fromTime)
                .toTime(toTime)
                .levels(levels)
                .availableThreads(new ArrayList<>(threadSet))
                .build();
    }

    private List<LogEntry> parseAll(String rawLogs) {
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
            if (entry != null) {
                result.add(entry);
            }
        }

        return result;
    }

    private LogEntry parseLine(String line, int lineNumber) {
        // 1. Spring Boot 포맷 시도
        Matcher springMatcher = SPRING_BOOT_LOG_PATTERN.matcher(line);
        if (springMatcher.matches()) {
            String timestampStr = springMatcher.group(1);
            String level = springMatcher.group(2).toUpperCase();
            String threadName = springMatcher.group(3).trim();
            String logger = springMatcher.group(4).trim();
            String message = springMatcher.group(5);

            if (level.equals("WARNING")) {
                level = "WARN";
            }

            return LogEntry.builder()
                    .timestamp(parseTimestamp(timestampStr))
                    .level(level)
                    .threadName(threadName)
                    .logger(logger)
                    .message(message)
                    .lineNumber(lineNumber)
                    .build();
        }

        // 2. 기존 단순 포맷 fallback
        Matcher simpleMatcher = SIMPLE_LOG_PATTERN.matcher(line);

        LocalDateTime timestamp = null;
        String level = "INFO";
        String message = line;

        if (simpleMatcher.matches()) {
            String timestampStr = simpleMatcher.group(1);
            String possibleLevel = simpleMatcher.group(2);
            String content = simpleMatcher.group(3);

            timestamp = parseTimestamp(timestampStr);

            if (possibleLevel != null && LOG_LEVELS.contains(possibleLevel.toUpperCase())) {
                level = possibleLevel.toUpperCase();
                message = possibleLevel + " " + (content != null ? content : "");
            } else {
                message = line.substring(line.indexOf('Z') + 1).trim();
                if (message.isEmpty()) {
                    message = line;
                }
                level = extractLevelFromMessage(message);
            }
        } else {
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
                // 나노초 정밀도 처리: "2026-04-16T04:09:22.682981716Z" → 밀리초까지만 사용
                String withoutZ = timestampStr.substring(0, timestampStr.length() - 1);
                // 밀리초까지만 잘라서 파싱 (최대 23자: yyyy-MM-ddTHH:mm:ss.SSS)
                String trimmed = withoutZ.length() > 23 ? withoutZ.substring(0, 23) : withoutZ;
                return LocalDateTime.parse(
                        trimmed,
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
                                    List<String> levels, String threadName, String traceId) {
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

        // 스레드명 필터
        if (threadName != null && !threadName.isEmpty()) {
            if (!threadName.equals(entry.getThreadName())) {
                return false;
            }
        }

        // traceId 필터 (메시지에서 workflowId 등 검색)
        if (traceId != null && !traceId.isEmpty()) {
            if (!entry.getMessage().contains(traceId)) {
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
