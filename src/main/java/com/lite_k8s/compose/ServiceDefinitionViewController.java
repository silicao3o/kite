package com.lite_k8s.compose;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ServiceDefinitionViewController {

    @GetMapping("/service-definitions")
    public String serviceDefinitions() {
        return "service-definitions";
    }
}
