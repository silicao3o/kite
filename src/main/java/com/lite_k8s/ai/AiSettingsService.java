package com.lite_k8s.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI 설정 서비스
 *
 * AI 제공자 및 API 키를 관리
 */
@Component
public class AiSettingsService {

    @Value("${docker.monitor.ai.enabled:false}")
    private boolean enabled;

    @Value("${docker.monitor.ai.provider:ANTHROPIC}")
    private String provider;

    @Value("${docker.monitor.ai.anthropic-api-key:}")
    private String anthropicApiKey;

    @Value("${docker.monitor.ai.openai-api-key:}")
    private String openaiApiKey;

    @Value("${docker.monitor.ai.gemini-api-key:}")
    private String geminiApiKey;

    public boolean isEnabled() {
        if (!enabled) {
            return false;
        }
        AiProvider aiProvider = getProviderEnum();
        if (aiProvider == AiProvider.CLAUDE_CODE) {
            return true;
        }
        String apiKey = getApiKeyForProvider(aiProvider);
        return apiKey != null && !apiKey.isEmpty();
    }

    public AiProvider getProviderEnum() {
        try {
            return AiProvider.valueOf(provider);
        } catch (IllegalArgumentException e) {
            return AiProvider.ANTHROPIC;
        }
    }

    public String getApiKeyForProvider(AiProvider aiProvider) {
        return switch (aiProvider) {
            case ANTHROPIC -> anthropicApiKey;
            case OPENAI -> openaiApiKey;
            case GEMINI -> geminiApiKey;
            case CLAUDE_CODE -> "";
        };
    }

    public boolean isRawEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getAnthropicApiKey() {
        return anthropicApiKey;
    }

    public void setAnthropicApiKey(String anthropicApiKey) {
        this.anthropicApiKey = anthropicApiKey;
    }

    public String getOpenaiApiKey() {
        return openaiApiKey;
    }

    public void setOpenaiApiKey(String openaiApiKey) {
        this.openaiApiKey = openaiApiKey;
    }

    public String getGeminiApiKey() {
        return geminiApiKey;
    }

    public void setGeminiApiKey(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey;
    }
}
