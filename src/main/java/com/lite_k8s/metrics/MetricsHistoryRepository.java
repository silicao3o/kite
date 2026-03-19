package com.lite_k8s.metrics;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class MetricsHistoryRepository {

    private static final int MAX_ENTRIES_PER_CONTAINER = 10_000;

    private final Map<String, List<MetricsSnapshot>> store = new ConcurrentHashMap<>();

    public void save(MetricsSnapshot snapshot) {
        store.compute(snapshot.getContainerName(), (key, list) -> {
            if (list == null) list = new ArrayList<>();
            list.add(snapshot);
            if (list.size() > MAX_ENTRIES_PER_CONTAINER) {
                list.remove(0);
            }
            return list;
        });
    }

    public List<MetricsSnapshot> findByContainerName(String containerName, LocalDateTime from) {
        List<MetricsSnapshot> all = store.getOrDefault(containerName, List.of());
        return all.stream()
                .filter(s -> s.getCollectedAt().isAfter(from))
                .sorted(Comparator.comparing(MetricsSnapshot::getCollectedAt))
                .collect(Collectors.toList());
    }

    public List<MetricsSnapshot> findByContainerNameAndRange(String containerName, LocalDateTime from, LocalDateTime to) {
        List<MetricsSnapshot> all = store.getOrDefault(containerName, List.of());
        return all.stream()
                .filter(s -> !s.getCollectedAt().isBefore(from) && s.getCollectedAt().isBefore(to))
                .sorted(Comparator.comparing(MetricsSnapshot::getCollectedAt))
                .collect(Collectors.toList());
    }

    public List<String> findAllContainerNames() {
        return new ArrayList<>(store.keySet());
    }

    public void deleteOlderThan(LocalDateTime cutoff) {
        store.forEach((name, list) -> list.removeIf(s -> s.getCollectedAt().isBefore(cutoff)));
    }
}
