package com.lite_k8s.metrics;

import com.lite_k8s.incident.IncidentReport;
import com.lite_k8s.incident.IncidentReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IncidentTimelineService {

    private final IncidentReportRepository incidentReportRepository;

    public List<TimelineEntry> getTimeline(int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(days);

        return incidentReportRepository.findAll().stream()
                .filter(r -> r.getCreatedAt().isAfter(from))
                .sorted(Comparator.comparing(IncidentReport::getCreatedAt).reversed())
                .map(this::toTimelineEntry)
                .collect(Collectors.toList());
    }

    private TimelineEntry toTimelineEntry(IncidentReport report) {
        return TimelineEntry.builder()
                .containerId(report.getContainerId())
                .containerName(report.getContainerName())
                .summary(report.getSummary())
                .status(report.getStatus().name())
                .occurredAt(report.getCreatedAt())
                .build();
    }
}
