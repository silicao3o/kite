package com.lite_k8s.metrics;

import com.lite_k8s.model.HealingEvent;
import com.lite_k8s.repository.HealingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HealingStatisticsService {

    private final HealingEventRepository healingEventRepository;

    public HealingStatistics getStatistics() {
        List<HealingEvent> all = healingEventRepository.findAll();

        long total = all.size();
        long success = all.stream().filter(HealingEvent::isSuccess).count();
        long failure = total - success;
        double successRate = total == 0 ? 0.0 : (double) success / total * 100.0;

        Map<String, Long> countPerContainer = all.stream()
                .collect(Collectors.groupingBy(HealingEvent::getContainerName, Collectors.counting()));

        return HealingStatistics.builder()
                .totalCount(total)
                .successCount(success)
                .failureCount(failure)
                .successRate(successRate)
                .countPerContainer(countPerContainer)
                .build();
    }
}
