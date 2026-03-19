package com.lite_k8s.approval;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 승인 관리 컨트롤러
 */
@Controller
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    /**
     * 승인 대기 목록 페이지
     */
    @GetMapping("/approvals")
    public String approvalsPage(Model model) {
        List<PendingApproval> pending = approvalService.getPendingRequests();
        List<PendingApproval> all = approvalService.findAll();

        model.addAttribute("pendingApprovals", pending);
        model.addAttribute("allApprovals", all);
        model.addAttribute("pendingCount", pending.size());

        return "approvals";
    }

    /**
     * 대기 중인 승인 목록 조회 (API)
     */
    @GetMapping("/api/approvals/pending")
    @ResponseBody
    public List<PendingApproval> getPendingApprovals() {
        return approvalService.getPendingRequests();
    }

    /**
     * 대기 중인 승인 개수 조회 (API)
     */
    @GetMapping("/api/approvals/pending/count")
    @ResponseBody
    public int getPendingCount() {
        return approvalService.getPendingRequests().size();
    }

    /**
     * 승인 처리 (API)
     */
    @PostMapping("/api/approvals/{id}/approve")
    @ResponseBody
    public ApprovalResult approve(
            @PathVariable String id,
            @RequestParam(defaultValue = "admin") String approver) {
        return approvalService.approve(id, approver);
    }

    /**
     * 거부 처리 (API)
     */
    @PostMapping("/api/approvals/{id}/reject")
    @ResponseBody
    public ApprovalResult reject(
            @PathVariable String id,
            @RequestParam(defaultValue = "admin") String approver) {
        return approvalService.reject(id, approver);
    }

    /**
     * 전체 승인 목록 조회 (API)
     */
    @GetMapping("/api/approvals")
    @ResponseBody
    public List<PendingApproval> getAllApprovals() {
        return approvalService.findAll();
    }
}
