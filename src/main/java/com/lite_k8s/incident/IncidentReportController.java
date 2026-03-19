package com.lite_k8s.incident;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class IncidentReportController {

    private final IncidentReportService incidentReportService;

    @GetMapping("/incidents")
    public String incidentsPage(Model model) {
        java.util.List<IncidentReport> reports = incidentReportService.findAll();
        model.addAttribute("reports", reports);
        model.addAttribute("openCount", reports.stream().filter(r -> r.getStatus() == IncidentReport.Status.OPEN).count());
        model.addAttribute("analyzingCount", reports.stream().filter(r -> r.getStatus() == IncidentReport.Status.ANALYZING).count());
        model.addAttribute("closedCount", reports.stream().filter(r -> r.getStatus() == IncidentReport.Status.CLOSED).count());
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
