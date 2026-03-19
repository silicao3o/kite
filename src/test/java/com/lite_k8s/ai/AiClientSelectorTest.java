package com.lite_k8s.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiClientSelectorTest {

    @Mock
    private AnthropicAiClient anthropicAiClient;

    @Mock
    private OpenAiClient openAiClient;

    @Mock
    private GeminiAiClient geminiAiClient;

    @Mock
    private ClaudeCodeClient claudeCodeClient;

    @Mock
    private AiSettingsService aiSettingsService;

    private AiClientSelector selector;

    @BeforeEach
    void setUp() {
        selector = new AiClientSelector(anthropicAiClient, openAiClient, geminiAiClient, claudeCodeClient, aiSettingsService);
    }

    @Test
    @DisplayName("ANTHROPIC 프로바이더 설정 시 AnthropicAiClient 반환")
    void shouldReturnAnthropicClientForAnthropicProvider() {
        when(aiSettingsService.getProviderEnum()).thenReturn(AiProvider.ANTHROPIC);

        AiClient client = selector.getClient();

        assertThat(client).isInstanceOf(AnthropicAiClient.class);
    }

    @Test
    @DisplayName("OPENAI 프로바이더 설정 시 OpenAiClient 반환")
    void shouldReturnOpenAiClientForOpenAiProvider() {
        when(aiSettingsService.getProviderEnum()).thenReturn(AiProvider.OPENAI);

        AiClient client = selector.getClient();

        assertThat(client).isInstanceOf(OpenAiClient.class);
    }

    @Test
    @DisplayName("GEMINI 프로바이더 설정 시 GeminiAiClient 반환")
    void shouldReturnGeminiClientForGeminiProvider() {
        when(aiSettingsService.getProviderEnum()).thenReturn(AiProvider.GEMINI);

        AiClient client = selector.getClient();

        assertThat(client).isInstanceOf(GeminiAiClient.class);
    }

    @Test
    @DisplayName("CLAUDE_CODE 프로바이더 설정 시 ClaudeCodeClient 반환")
    void shouldReturnClaudeCodeClientForClaudeCodeProvider() {
        when(aiSettingsService.getProviderEnum()).thenReturn(AiProvider.CLAUDE_CODE);

        AiClient client = selector.getClient();

        assertThat(client).isInstanceOf(ClaudeCodeClient.class);
    }

    @Test
    @DisplayName("isEnabled는 AiSettingsService에 위임한다")
    void shouldDelegateIsEnabledToSettingsService() {
        when(aiSettingsService.isEnabled()).thenReturn(true);
        assertThat(selector.isEnabled()).isTrue();

        when(aiSettingsService.isEnabled()).thenReturn(false);
        assertThat(selector.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("analyzeWithPrompt는 선택된 클라이언트에 위임한다")
    void shouldDelegateAnalyzeWithPromptToSelectedClient() {
        when(aiSettingsService.getProviderEnum()).thenReturn(AiProvider.ANTHROPIC);
        ClaudeResponse expectedResponse = ClaudeResponse.success("restart", "test reasoning", "LOW", 0.9, "raw");
        when(anthropicAiClient.analyzeWithPrompt("test prompt")).thenReturn(expectedResponse);

        ClaudeResponse result = selector.analyzeWithPrompt("test prompt");

        assertThat(result).isEqualTo(expectedResponse);
    }
}
