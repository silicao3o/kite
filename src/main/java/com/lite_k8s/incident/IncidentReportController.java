package com.lite_k8s.incident;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class IncidentReportController {

    private final IncidentReportService incidentReportService;

    @GetMapping("/incidents")
    public String incidentsPage(@RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "50") int size,
                                Model model) {
        // 사용자가 보내는 1부터 시작하는 페이지 번호를 Spring 내부의 0부터로 변환
        int zeroBasedPage = page - 1;
        if (zeroBasedPage < 0) zeroBasedPage = 0;

        // 페이지네이션으로 현재 페이지의 인시던트만 조회 (최대 50건)
        Page<IncidentReport> reportPage = incidentReportService.findAll(zeroBasedPage, size);

        // 페이지 데이터
        model.addAttribute("reports", reportPage.getContent());

        // 페이지 메타정보 — 화면에는 1부터 시작하는 번호로 노출
        model.addAttribute("currentPage", reportPage.getNumber() + 1);      // 현재 페이지 번호 (1부터)
        model.addAttribute("totalPages", reportPage.getTotalPages());       // 전체 페이지 수
        model.addAttribute("totalElements", reportPage.getTotalElements()); // 전체 데이터 개수
        model.addAttribute("hasNext", reportPage.hasNext());                 // 다음 페이지 존재 여부
        model.addAttribute("hasPrevious", reportPage.hasPrevious());         // 이전 페이지 존재 여부
        model.addAttribute("pageSize", size);                                // 페이지 크기 (URL 생성용)

        // 통계 카운트는 페이지네이션과 무관하게 전체 데이터 기준으로 별도 쿼리
        model.addAttribute("openCount", incidentReportService.countByStatus(IncidentReport.Status.OPEN));
        model.addAttribute("analyzingCount", incidentReportService.countByStatus(IncidentReport.Status.ANALYZING));
        model.addAttribute("closedCount", incidentReportService.countByStatus(IncidentReport.Status.CLOSED));

        return "incidents";
    }

    @GetMapping("/incidents/{id}")
    public String incidentDetailPage(@PathVariable String id, Model model) {
        Optional<IncidentReport> report = incidentReportService.findById(id);
        if (report.isEmpty()) {
            return "redirect:/incidents";
        }
        model.addAttribute("report", report.get());
        return "incident-detail";
    }
}
