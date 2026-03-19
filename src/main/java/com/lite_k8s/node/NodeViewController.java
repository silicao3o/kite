package com.lite_k8s.node;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class NodeViewController {

    private final NodeRegistry nodeRegistry;

    @GetMapping("/nodes")
    public String nodes(Model model) {
        model.addAttribute("nodes", nodeRegistry.findAll());
        return "nodes";
    }
}
