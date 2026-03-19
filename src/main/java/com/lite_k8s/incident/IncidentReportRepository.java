package com.lite_k8s.incident;

import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class IncidentReportRepository {

    private final Map<String, IncidentReport> store = new ConcurrentHashMap<>();

    public void save(IncidentReport report) {
        store.put(report.getId(), report);
    }

    public Optional<IncidentReport> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<IncidentReport> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(IncidentReport::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<IncidentReport> findByContainerName(String containerName) {
        return store.values().stream()
                .filter(r -> containerName.equals(r.getContainerName()))
                .sorted(Comparator.comparing(IncidentReport::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public void clear() {
        store.clear();
    }
}
