package com.lite_k8s.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Services 와 Env Profiles 를 한 페이지의 탭으로 묶은 wrapper.
 *
 * 두 페이지(/service-definitions, /env-profiles) 는 그대로 유지 — 이 페이지는
 * iframe 으로 둘을 감싸는 얇은 wrapper. 사이드바에서 둘을 한 항목 'Deployments'
 * 로 통합해도 깊은 링크 (북마크) 는 보존된다.
 */
@Controller
public class DeploymentsViewController {

    @GetMapping("/deployments")
    public String deployments(@RequestParam(required = false, defaultValue = "services") String tab,
                              Model model) {
        model.addAttribute("activeTab", "profiles".equalsIgnoreCase(tab) ? "profiles" : "services");
        return "deployments";
    }
}
