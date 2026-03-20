package com.lite_k8s.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Anthropic API 클라이언트
 *
 * claude-haiku-4-5-20251001 모델 사용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnthropicAiClient implements AiClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    private final AiSettingsService aiSettingsService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public ClaudeResponse analyzeWithPrompt(String prompt) {
        String apiKey = aiSettingsService.getAnthropicApiKey();
        try {
            String requestBody = buildRequestBody(prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Anthropic API returned status: {} body: {}", response.statusCode(), response.body());
                return ClaudeResponse.error("API returned status: " + response.statusCode());
            }

            String responseText = extractResponseText(response.body());
            return AiResponseParser.parse(responseText);

        } catch (Exception e) {
            log.error("Failed to call Anthropic API", e);
            return ClaudeResponse.error("Anthropic API call failed: " + e.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        if (!aiSettingsService.isEnabled()) {
            return false;
        }
        if (aiSettingsService.getProviderEnum() != AiProvider.ANTHROPIC) {
            return false;
        }
        String apiKey = aiSettingsService.getAnthropicApiKey();
        return apiKey != null && !apiKey.isEmpty();
    }

    private String buildRequestBody(String prompt) throws Exception {
        String escapedPrompt = objectMapper.writeValueAsString(prompt);
        return String.format(
                "{\"model\":\"%s\",\"max_tokens\":1024,\"messages\":[{\"role\":\"user\",\"content\":%s}]}",
                aiSettingsService.getAnthropicModel(), escapedPrompt
        );
    }

    private String extractResponseText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode content = root.path("content");
        if (content.isArray() && content.size() > 0) {
            return content.get(0).path("text").asText();
        }
        return responseBody;
    }
}
