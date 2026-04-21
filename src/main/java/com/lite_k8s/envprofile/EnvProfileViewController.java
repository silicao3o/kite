package com.lite_k8s.envprofile;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EnvProfileViewController {

    @GetMapping("/env-profiles")
    public String envProfiles() {
        return "env-profiles";
    }
}
