package com.lite_k8s.incident;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class IncidentPatternDetector {

    private static final int PATTERN_THRESHOLD = 3;
    private static final int WINDOW_HOURS = 24;

    private final IncidentReportRepository repository;

    public Optional<IncidentPattern> detectPattern(String containerName) {
        LocalDateTime windowStart = LocalDateTime.now().minusHours(WINDOW_HOURS);

        List<IncidentReport> recent = repository.findByContainerName(containerName).stream()
                .filter(r -> r.getCreatedAt().isAfter(windowStart))
                .collect(Collectors.toList());

        if (recent.size() < PATTERN_THRESHOLD) {
            return Optional.empty();
        }

        return Optional.of(buildPattern(containerName, recent));
    }

    public List<IncidentPattern> detectAllPatterns() {
        LocalDateTime windowStart = LocalDateTime.now().minusHours(WINDOW_HOURS);

        Map<String, List<IncidentReport>> byContainer = repository.findAll().stream()
                .filter(r -> r.getCreatedAt().isAfter(windowStart))
                .collect(Collectors.groupingBy(IncidentReport::getContainerName));

        return byContainer.entrySet().stream()
                .filter(e -> e.getValue().size() >= PATTERN_THRESHOLD)
                .map(e -> buildPattern(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private IncidentPattern buildPattern(String containerName, List<IncidentReport> reports) {
        LocalDateTime first = reports.stream()
                .map(IncidentReport::getCreatedAt)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
        LocalDateTime last = reports.stream()
                .map(IncidentReport::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        String commonSummary = reports.get(0).getSummary();

        return IncidentPattern.builder()
                .containerName(containerName)
                .occurrenceCount(reports.size())
                .firstOccurrence(first)
                .lastOccurrence(last)
                .commonSummary(commonSummary)
                .build();
    }
}
