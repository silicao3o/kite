package com.lite_k8s.incident;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class IncidentReportRepository {

    private final IncidentReportJpaRepository jpa;

    public void save(IncidentReport report) {
        jpa.save(report);
    }

    public Optional<IncidentReport> findById(String id) {
        return jpa.findById(id);
    }

    public List<IncidentReport> findAll() {
        return jpa.findAllByOrderByCreatedAtDesc();
    }

    public List<IncidentReport> findByContainerName(String containerName) {
        return jpa.findByContainerNameOrderByCreatedAtDesc(containerName);
    }

    public void clear() {
        jpa.deleteAll();
    }
}
