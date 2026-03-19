package com.lite_k8s.incident;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class SuggestionController {

    private final SuggestionService suggestionService;

    @GetMapping("/suggestions")
    public String suggestionsPage(Model model) {
        model.addAttribute("suggestions", suggestionService.findAll());
        model.addAttribute("pendingCount", suggestionService.findPending().size());
        return "suggestions";
    }

    @PostMapping("/suggestions/{id}/approve")
    public String approve(@PathVariable String id, RedirectAttributes redirectAttributes) {
        suggestionService.approve(id);
        redirectAttributes.addFlashAttribute("message", "제안이 승인되었습니다.");
        return "redirect:/suggestions";
    }

    @PostMapping("/suggestions/{id}/reject")
    public String reject(@PathVariable String id, RedirectAttributes redirectAttributes) {
        suggestionService.reject(id);
        redirectAttributes.addFlashAttribute("message", "제안이 거부되었습니다.");
        return "redirect:/suggestions";
    }
}
