package com.lite_k8s.ai;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI 설정 서비스
 *
 * 시작 시 DB에서 설정 로드, 변경 시 DB에 저장.
 * DB에 레코드가 없으면 환경변수 기본값 사용.
 */
@Component
@RequiredArgsConstructor
public class AiSettingsService {

    private final AiSettingsJpaRepository jpaRepository;

    @Value("${docker.monitor.ai.enabled:false}")
    private boolean defaultEnabled;

    @Value("${docker.monitor.ai.provider:ANTHROPIC}")
    private String defaultProvider;

    @Value("${docker.monitor.ai.anthropic-api-key:}")
    private String defaultAnthropicApiKey;

    @Value("${docker.monitor.ai.openai-api-key:}")
    private String defaultOpenaiApiKey;

    @Value("${docker.monitor.ai.gemini-api-key:}")
    private String defaultGeminiApiKey;

    private boolean enabled;
    private String provider;
    private String anthropicApiKey;
    private String openaiApiKey;
    private String geminiApiKey;
    private String anthropicModel = "claude-haiku-4-5-20251001";
    private String openaiModel = "gpt-4o-mini";
    private String geminiModel = "gemini-2.0-flash";

    @PostConstruct
    public void init() {
        AiSettings settings = jpaRepository.findById("default")
                .orElse(null);

        if (settings != null) {
            this.enabled = settings.isEnabled();
            this.provider = settings.getProvider().name();
            this.anthropicApiKey = settings.getAnthropicApiKey();
            this.openaiApiKey = settings.getOpenaiApiKey();
            this.geminiApiKey = settings.getGeminiApiKey();
            this.anthropicModel = settings.getAnthropicModel();
            this.openaiModel = settings.getOpenaiModel();
            this.geminiModel = settings.getGeminiModel();
        } else {
            this.enabled = defaultEnabled;
            this.provider = defaultProvider;
            this.anthropicApiKey = defaultAnthropicApiKey;
            this.openaiApiKey = defaultOpenaiApiKey;
            this.geminiApiKey = defaultGeminiApiKey;
        }
    }

    private void persist() {
        AiSettings settings = jpaRepository.findById("default")
                .orElse(AiSettings.defaultSettings());
        settings.setEnabled(this.enabled);
        try {
            settings.setProvider(AiProvider.valueOf(this.provider));
        } catch (IllegalArgumentException e) {
            settings.setProvider(AiProvider.ANTHROPIC);
        }
        settings.setAnthropicApiKey(this.anthropicApiKey);
        settings.setOpenaiApiKey(this.openaiApiKey);
        settings.setGeminiApiKey(this.geminiApiKey);
        settings.setAnthropicModel(this.anthropicModel);
        settings.setOpenaiModel(this.openaiModel);
        settings.setGeminiModel(this.geminiModel);
        jpaRepository.save(settings);
    }

    public boolean isEnabled() {
        if (!enabled) return false;
        AiProvider aiProvider = getProviderEnum();
        if (aiProvider == AiProvider.CLAUDE_CODE) return true;
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

    public boolean isRawEnabled() { return enabled; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        persist();
    }

    public String getProvider() { return provider; }

    public void setProvider(String provider) {
        this.provider = provider;
        persist();
    }

    public String getAnthropicApiKey() { return anthropicApiKey; }

    public void setAnthropicApiKey(String anthropicApiKey) {
        this.anthropicApiKey = anthropicApiKey;
        persist();
    }

    public String getOpenaiApiKey() { return openaiApiKey; }

    public void setOpenaiApiKey(String openaiApiKey) {
        this.openaiApiKey = openaiApiKey;
        persist();
    }

    public String getGeminiApiKey() { return geminiApiKey; }

    public void setGeminiApiKey(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey;
        persist();
    }

    public String getAnthropicModel() { return anthropicModel; }

    public void setAnthropicModel(String anthropicModel) {
        this.anthropicModel = anthropicModel;
        persist();
    }

    public String getOpenaiModel() { return openaiModel; }

    public void setOpenaiModel(String openaiModel) {
        this.openaiModel = openaiModel;
        persist();
    }

    public String getGeminiModel() { return geminiModel; }

    public void setGeminiModel(String geminiModel) {
        this.geminiModel = geminiModel;
        persist();
    }
}
