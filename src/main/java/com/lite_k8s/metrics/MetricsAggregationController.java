package com.lite_k8s.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/metrics-aggregation")
@RequiredArgsConstructor
public class MetricsAggregationController {

    private final MetricsAggregationService aggregationService;

    @GetMapping("/hourly-avg")
    public List<HourlyAggregate> getHourlyAverage(@RequestParam String container,
                                                    @RequestParam String from,
                                                    @RequestParam String to) {
        return aggregationService.getHourlyAverage(container, LocalDateTime.parse(from), LocalDateTime.parse(to));
    }

    @GetMapping("/hourly-max")
    public List<HourlyAggregate> getHourlyMax(@RequestParam String container,
                                               @RequestParam String from,
                                               @RequestParam String to) {
        return aggregationService.getHourlyMax(container, LocalDateTime.parse(from), LocalDateTime.parse(to));
    }

    @GetMapping("/stats")
    public ContainerStats getContainerStats(@RequestParam String container,
                                             @RequestParam(defaultValue = "24") int hours) {
        return aggregationService.getContainerStats(container, hours);
    }
}
