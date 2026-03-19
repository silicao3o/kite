package com.lite_k8s.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MultiContainerMetricsService {

    private final MetricsHistoryService metricsHistoryService;

    public Map<String, List<MetricsSnapshot>> getComparisonData(List<String> containerNames, int hours) {
        Map<String, List<MetricsSnapshot>> result = new LinkedHashMap<>();
        for (String name : containerNames) {
            result.put(name, metricsHistoryService.getHistory(name, hours));
        }
        return result;
    }
}
