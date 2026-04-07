package com.lite_k8s.desired;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DesiredStateViewController {

    @GetMapping("/desired-state")
    public String desiredState() {
        return "desired-state";
    }
}
