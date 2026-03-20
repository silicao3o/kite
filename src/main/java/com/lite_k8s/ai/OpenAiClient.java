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
 * OpenAI API 클라이언트
 *
 * gpt-4o-mini 모델 사용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiClient implements AiClient {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    private final AiSettingsService aiSettingsService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public ClaudeResponse analyzeWithPrompt(String prompt) {
        String apiKey = aiSettingsService.getOpenaiApiKey();
        try {
            String requestBody = buildRequestBody(prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("OpenAI API returned status: {}", response.statusCode());
                return ClaudeResponse.error("API returned status: " + response.statusCode());
            }

            String responseText = extractResponseText(response.body());
            return AiResponseParser.parse(responseText);

        } catch (Exception e) {
            log.error("Failed to call OpenAI API", e);
            return ClaudeResponse.error("OpenAI API call failed: " + e.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        if (!aiSettingsService.isEnabled()) {
            return false;
        }
        if (aiSettingsService.getProviderEnum() != AiProvider.OPENAI) {
            return false;
        }
        String apiKey = aiSettingsService.getOpenaiApiKey();
        return apiKey != null && !apiKey.isEmpty();
    }

    private String buildRequestBody(String prompt) throws Exception {
        String escapedPrompt = objectMapper.writeValueAsString(prompt);
        return String.format(
                "{\"model\":\"%s\",\"max_tokens\":1024,\"messages\":[{\"role\":\"user\",\"content\":%s}]}",
                aiSettingsService.getOpenaiModel(), escapedPrompt
        );
    }

    private String extractResponseText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("choices").get(0).path("message").path("content").asText();
    }
}
