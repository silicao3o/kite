package com.lite_k8s.envprofile;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class EnvProfileViewController {

    @GetMapping("/env-profiles")
    public String envProfiles(@RequestParam(required = false, defaultValue = "false") boolean embedded,
                               Model model) {
        // /deployments 탭 wrapper 가 iframe 으로 부르면 sidebar/page-header 숨김
        model.addAttribute("embedded", embedded);
        return "env-profiles";
    }
}
