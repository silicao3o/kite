package com.lite_k8s.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.lite_k8s.model.ContainerInfo;
import com.lite_k8s.service.MetricsScheduler;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class MetricsHistoryController {

    private final MetricsHistoryService metricsHistoryService;
    private final MultiContainerMetricsService multiContainerMetricsService;
    private final HealingStatisticsService healingStatisticsService;
    private final MetricsCsvExporter csvExporter;
    private final MetricsScheduler metricsScheduler;
    // [제거됨] private final IncidentTimelineService incidentTimelineService;
    // → incidents 페이지와 데이터가 중복되어 metrics-history에서는 표시하지 않음

    @GetMapping("/metrics-history")
    public String metricsHistoryPage(Model model,
                                     @RequestParam(defaultValue = "24") int hours) {
        List<String> containers = metricsHistoryService.getAllContainerNames();
        HealingStatistics healingStats = healingStatisticsService.getStatistics();
        // [제거됨] List<TimelineEntry> timeline = incidentTimelineService.getTimeline(7);
        // → 인시던트 타임라인은 incidents 페이지에서 페이지네이션과 함께 제공

        Map<String, String> containerNodeMap = metricsScheduler.getCachedContainers().stream()
                .collect(Collectors.toMap(
                        ContainerInfo::getName,
                        c -> c.getNodeName() != null ? c.getNodeName() : "local",
                        (a, b) -> a,
                        LinkedHashMap::new));

        model.addAttribute("containers", containers);
        model.addAttribute("containerNodeMap", containerNodeMap);
        model.addAttribute("healingStats", healingStats);
        // [제거됨] model.addAttribute("timeline", timeline);
        model.addAttribute("hours", hours);
        return "metrics-history";
    }

    @GetMapping("/api/metrics-history")
    @ResponseBody
    public List<MetricsSnapshot> getMetricsHistory(@RequestParam String container,
                                                    @RequestParam(defaultValue = "24") int hours) {
        return metricsHistoryService.getHistory(container, hours);
    }

    @GetMapping("/api/metrics-history/range")
    @ResponseBody
    public List<MetricsSnapshot> getMetricsHistoryByRange(@RequestParam String container,
                                                           @RequestParam String from,
                                                           @RequestParam String to) {
        LocalDateTime fromTime = LocalDateTime.parse(from);
        LocalDateTime toTime = LocalDateTime.parse(to);
        return metricsHistoryService.getHistoryByRange(container, fromTime, toTime);
    }

    @GetMapping("/api/metrics-history/compare")
    @ResponseBody
    public Map<String, List<MetricsSnapshot>> compareContainers(
            @RequestParam List<String> containers,
            @RequestParam(defaultValue = "24") int hours) {
        return multiContainerMetricsService.getComparisonData(containers, hours);
    }

    @GetMapping("/api/metrics-history/csv")
    public ResponseEntity<byte[]> downloadCsv(@RequestParam String container,
                                               @RequestParam(defaultValue = "24") int hours) {
        String csv = csvExporter.exportMetrics(container, hours);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"metrics-" + container + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }
}
