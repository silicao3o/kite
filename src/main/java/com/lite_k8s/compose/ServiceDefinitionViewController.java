package com.lite_k8s.compose;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ServiceDefinitionViewController {

    @GetMapping("/service-definitions")
    public String serviceDefinitions(@RequestParam(required = false, defaultValue = "false") boolean embedded,
                                      Model model) {
        // /deployments 탭 wrapper 가 iframe 으로 부르면 sidebar 와 page-header 를 숨겨
        // 중복 sidebar 노출을 방지한다.
        model.addAttribute("embedded", embedded);
        return "service-definitions";
    }
}
