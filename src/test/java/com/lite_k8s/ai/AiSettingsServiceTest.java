package com.lite_k8s.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiSettingsServiceTest {

    private AiSettingsService service;

    @BeforeEach
    void setUp() {
        service = new AiSettingsService();
    }

    @Test
    @DisplayName("기본값으로 AI가 비활성화된다")
    void shouldBeDisabledByDefault() {
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("enabled=true이고 API 키가 있으면 활성화된다")
    void shouldBeEnabledWhenEnabledAndApiKeyPresent() {
        service.setEnabled(true);
        service.setProvider("ANTHROPIC");
        service.setAnthropicApiKey("sk-ant-test-key");

        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("enabled=true이지만 API 키가 없으면 비활성화된다")
    void shouldBeDisabledWhenEnabledButNoApiKey() {
        service.setEnabled(true);
        service.setProvider("ANTHROPIC");
        service.setAnthropicApiKey("");

        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("CLAUDE_CODE 프로바이더는 API 키 없이 활성화 가능")
    void shouldBeEnabledForClaudeCodeWithoutApiKey() {
        service.setEnabled(true);
        service.setProvider("CLAUDE_CODE");

        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("OPENAI 프로바이더로 전환 가능")
    void shouldSwitchToOpenAiProvider() {
        service.setProvider("OPENAI");

        assertThat(service.getProviderEnum()).isEqualTo(AiProvider.OPENAI);
    }

    @Test
    @DisplayName("GEMINI 프로바이더로 전환 가능")
    void shouldSwitchToGeminiProvider() {
        service.setProvider("GEMINI");

        assertThat(service.getProviderEnum()).isEqualTo(AiProvider.GEMINI);
    }

    @Test
    @DisplayName("OPENAI 키가 있을 때 OPENAI 프로바이더 활성화")
    void shouldBeEnabledForOpenAiWhenApiKeyPresent() {
        service.setEnabled(true);
        service.setProvider("OPENAI");
        service.setOpenaiApiKey("sk-test-openai-key");

        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("GEMINI 키가 있을 때 GEMINI 프로바이더 활성화")
    void shouldBeEnabledForGeminiWhenApiKeyPresent() {
        service.setEnabled(true);
        service.setProvider("GEMINI");
        service.setGeminiApiKey("test-gemini-key");

        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("잘못된 프로바이더 이름은 ANTHROPIC으로 기본 설정")
    void shouldDefaultToAnthropicForInvalidProvider() {
        service.setProvider("INVALID_PROVIDER");

        assertThat(service.getProviderEnum()).isEqualTo(AiProvider.ANTHROPIC);
    }

    @Test
    @DisplayName("API 키를 업데이트할 수 있다")
    void shouldUpdateApiKeys() {
        service.setAnthropicApiKey("new-anthropic-key");
        service.setOpenaiApiKey("new-openai-key");
        service.setGeminiApiKey("new-gemini-key");

        assertThat(service.getAnthropicApiKey()).isEqualTo("new-anthropic-key");
        assertThat(service.getOpenaiApiKey()).isEqualTo("new-openai-key");
        assertThat(service.getGeminiApiKey()).isEqualTo("new-gemini-key");
    }

    @Test
    @DisplayName("enabled=false이면 API 키 있어도 비활성화")
    void shouldBeDisabledEvenWithApiKeyWhenNotEnabled() {
        service.setEnabled(false);
        service.setProvider("ANTHROPIC");
        service.setAnthropicApiKey("sk-ant-test-key");

        assertThat(service.isEnabled()).isFalse();
    }
}
