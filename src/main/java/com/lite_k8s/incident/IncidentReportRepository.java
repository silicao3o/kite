package com.lite_k8s.incident;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
    // 페이지네이션용 — 페이지 번호와 크기를 받아서 해당 페이지만 조회
    public Page<IncidentReport> findAll(Pageable pageable) {
        return jpa.findAllByOrderByCreatedAtDesc(pageable);
    }

    // 페이지와 무관하게 status별 전체 카운트
    public long countByStatus(IncidentReport.Status status) {
        return jpa.countByStatus(status);
    }
    public void clear() {
        jpa.deleteAll();
    }
}
