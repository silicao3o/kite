package com.lite_k8s.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * AI 설정 컨트롤러
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AiSettingsController {

    private final AiSettingsService aiSettingsService;

    @GetMapping("/ai-settings")
    public String settingsPage(Model model) {
        model.addAttribute("enabled", aiSettingsService.isRawEnabled());
        model.addAttribute("provider", aiSettingsService.getProvider());
        model.addAttribute("anthropicKeyMasked", maskKey(aiSettingsService.getAnthropicApiKey()));
        model.addAttribute("openaiKeyMasked", maskKey(aiSettingsService.getOpenaiApiKey()));
        model.addAttribute("geminiKeyMasked", maskKey(aiSettingsService.getGeminiApiKey()));
        model.addAttribute("aiActive", aiSettingsService.isEnabled());
        return "ai-settings";
    }

    @PostMapping("/api/ai/settings")
    @ResponseBody
    public ResponseEntity<AiSettingsResponse> updateSettings(@RequestBody AiSettingsRequest request) {
        if (request.enabled() != null) {
            aiSettingsService.setEnabled(request.enabled());
        }
        if (request.provider() != null) {
            aiSettingsService.setProvider(request.provider());
        }
        if (request.anthropicApiKey() != null && !request.anthropicApiKey().isEmpty()) {
            aiSettingsService.setAnthropicApiKey(request.anthropicApiKey());
        }
        if (request.openaiApiKey() != null && !request.openaiApiKey().isEmpty()) {
            aiSettingsService.setOpenaiApiKey(request.openaiApiKey());
        }
        if (request.geminiApiKey() != null && !request.geminiApiKey().isEmpty()) {
            aiSettingsService.setGeminiApiKey(request.geminiApiKey());
        }

        log.info("AI settings updated: enabled={}, provider={}", aiSettingsService.isRawEnabled(), aiSettingsService.getProvider());

        AiSettingsResponse response = buildResponse();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/ai/settings")
    @ResponseBody
    public ResponseEntity<AiSettingsResponse> getSettings() {
        return ResponseEntity.ok(buildResponse());
    }

    private AiSettingsResponse buildResponse() {
        return new AiSettingsResponse(
                aiSettingsService.isRawEnabled(),
                aiSettingsService.getProvider(),
                maskKey(aiSettingsService.getAnthropicApiKey()),
                maskKey(aiSettingsService.getOpenaiApiKey()),
                maskKey(aiSettingsService.getGeminiApiKey())
        );
    }

    private String maskKey(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        if (key.length() <= 4) {
            return "••••";
        }
        return "••••••••" + key.substring(key.length() - 4);
    }

    public record AiSettingsRequest(
            Boolean enabled,
            String provider,
            String anthropicApiKey,
            String openaiApiKey,
            String geminiApiKey
    ) {}

    public record AiSettingsResponse(
            boolean enabled,
            String provider,
            String anthropicApiKeyMasked,
            String openaiApiKeyMasked,
            String geminiApiKeyMasked
    ) {}
}
