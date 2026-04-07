package com.lite_k8s.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface IncidentReportJpaRepository extends JpaRepository<IncidentReport, String> {
    List<IncidentReport> findAllByOrderByCreatedAtDesc();
    List<IncidentReport> findByContainerNameOrderByCreatedAtDesc(String containerName);

    //페이지네이션용 - 전체 데이터를 정렬해서 페이지 단위로 조회
    Page<IncidentReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    //페이지네이션 시 전체 통계 카운트용 - status별 조회
    long countByStatus(IncidentReport.Status status);
}
