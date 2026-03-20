package com.lite_k8s.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentReportJpaRepository extends JpaRepository<IncidentReport, String> {
    List<IncidentReport> findAllByOrderByCreatedAtDesc();
    List<IncidentReport> findByContainerNameOrderByCreatedAtDesc(String containerName);
}
