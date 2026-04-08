package com.lite_k8s.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class NotificationRulesViewController {

    @GetMapping("/notification-rules")
    public String notificationRules() {
        return "notification-rules";
    }
}
