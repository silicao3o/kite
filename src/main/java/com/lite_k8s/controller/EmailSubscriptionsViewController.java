package com.lite_k8s.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EmailSubscriptionsViewController {

    @GetMapping("/email-subscriptions")
    public String emailSubscriptions() {
        return "email-subscriptions";
    }
}
