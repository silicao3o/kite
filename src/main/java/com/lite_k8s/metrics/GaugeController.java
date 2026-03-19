package com.lite_k8s.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/gauges")
@RequiredArgsConstructor
public class GaugeController {

    private static final int REFRESH_INTERVAL_SECONDS = 30;

    private final CurrentMetricsGaugeService gaugeService;

    @GetMapping
    public List<GaugeData> getAllGauges() {
        return gaugeService.getAllGauges();
    }

    @GetMapping("/{containerId}")
    public Optional<GaugeData> getGauge(@PathVariable String containerId) {
        return gaugeService.getGauge(containerId);
    }

    @GetMapping("/refresh-interval")
    public int getRefreshInterval() {
        return REFRESH_INTERVAL_SECONDS;
    }
}
