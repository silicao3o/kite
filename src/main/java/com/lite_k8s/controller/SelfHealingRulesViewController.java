package com.lite_k8s.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SelfHealingRulesViewController {

    @GetMapping("/self-healing-rules")
    public String selfHealingRules() {
        return "self-healing-rules";
    }
}
