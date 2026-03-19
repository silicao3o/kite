package com.lite_k8s.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 클라이언트 선택기
 *
 * 설정에 따라 적절한 AI 클라이언트를 선택하여 분석 요청을 위임
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClientSelector {

    private final AnthropicAiClient anthropicAiClient;
    private final OpenAiClient openAiClient;
    private final GeminiAiClient geminiAiClient;
    private final ClaudeCodeClient claudeCodeClient;
    private final AiSettingsService aiSettingsService;

    public AiClient getClient() {
        AiProvider provider = aiSettingsService.getProviderEnum();
        return switch (provider) {
            case ANTHROPIC -> anthropicAiClient;
            case OPENAI -> openAiClient;
            case GEMINI -> geminiAiClient;
            case CLAUDE_CODE -> claudeCodeClient;
        };
    }

    public ClaudeResponse analyzeWithPrompt(String prompt) {
        return getClient().analyzeWithPrompt(prompt);
    }

    public boolean isEnabled() {
        return aiSettingsService.isEnabled();
    }
}
