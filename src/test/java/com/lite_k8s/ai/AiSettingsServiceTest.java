package com.lite_k8s.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiSettingsServiceTest {

    @Mock
    private AiSettingsJpaRepository jpaRepository;

    private AiSettingsService service;

    @BeforeEach
    void setUp() {
        when(jpaRepository.findById("default")).thenReturn(Optional.empty());
        when(jpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new AiSettingsService(jpaRepository);
        // @Value 필드 수동 주입 (MockitoExtension에서는 @Value가 주입되지 않음)
        ReflectionTestUtils.setField(service, "defaultEnabled", false);
        ReflectionTestUtils.setField(service, "defaultProvider", "ANTHROPIC");
        ReflectionTestUtils.setField(service, "defaultAnthropicApiKey", "");
        ReflectionTestUtils.setField(service, "defaultOpenaiApiKey", "");
        ReflectionTestUtils.setField(service, "defaultGeminiApiKey", "");
        service.init();
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

    @Test
    @DisplayName("Anthropic 기본 모델은 claude-haiku-4-5-20251001")
    void shouldHaveDefaultAnthropicModel() {
        assertThat(service.getAnthropicModel()).isEqualTo("claude-haiku-4-5-20251001");
    }

    @Test
    @DisplayName("OpenAI 기본 모델은 gpt-4o-mini")
    void shouldHaveDefaultOpenAiModel() {
        assertThat(service.getOpenaiModel()).isEqualTo("gpt-4o-mini");
    }

    @Test
    @DisplayName("Gemini 기본 모델은 gemini-2.0-flash")
    void shouldHaveDefaultGeminiModel() {
        assertThat(service.getGeminiModel()).isEqualTo("gemini-2.0-flash");
    }

    @Test
    @DisplayName("각 프로바이더 모델을 변경할 수 있다")
    void shouldUpdateModels() {
        service.setAnthropicModel("claude-opus-4-6");
        service.setOpenaiModel("gpt-4o");
        service.setGeminiModel("gemini-1.5-pro");

        assertThat(service.getAnthropicModel()).isEqualTo("claude-opus-4-6");
        assertThat(service.getOpenaiModel()).isEqualTo("gpt-4o");
        assertThat(service.getGeminiModel()).isEqualTo("gemini-1.5-pro");
    }

    @Test
    @DisplayName("설정 변경 시 DB에 저장된다")
    void shouldPersistOnChange() {
        service.setEnabled(true);

        verify(jpaRepository, atLeastOnce()).save(any(AiSettings.class));
    }

    @Test
    @DisplayName("DB에 기존 설정이 있으면 로드한다")
    void shouldLoadFromDbOnInit() {
        AiSettings stored = AiSettings.defaultSettings();
        stored.setEnabled(true);
        stored.setProvider(AiProvider.GEMINI);
        stored.setGeminiApiKey("stored-key");
        when(jpaRepository.findById("default")).thenReturn(Optional.of(stored));

        service = new AiSettingsService(jpaRepository);
        service.init();

        assertThat(service.isRawEnabled()).isTrue();
        assertThat(service.getProviderEnum()).isEqualTo(AiProvider.GEMINI);
        assertThat(service.getGeminiApiKey()).isEqualTo("stored-key");
    }
}
