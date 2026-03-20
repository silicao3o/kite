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
 * Google Gemini API 클라이언트
 *
 * gemini-2.0-flash 모델 사용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiAiClient implements AiClient {

    private static final String API_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final AiSettingsService aiSettingsService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public ClaudeResponse analyzeWithPrompt(String prompt) {
        String apiKey = aiSettingsService.getGeminiApiKey();
        try {
            String url = String.format(API_URL_TEMPLATE, aiSettingsService.getGeminiModel(), apiKey);
            String requestBody = buildRequestBody(prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Gemini API returned status: {}", response.statusCode());
                return ClaudeResponse.error("API returned status: " + response.statusCode());
            }

            String responseText = extractResponseText(response.body());
            return AiResponseParser.parse(responseText);

        } catch (Exception e) {
            log.error("Failed to call Gemini API", e);
            return ClaudeResponse.error("Gemini API call failed: " + e.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        if (!aiSettingsService.isEnabled()) {
            return false;
        }
        if (aiSettingsService.getProviderEnum() != AiProvider.GEMINI) {
            return false;
        }
        String apiKey = aiSettingsService.getGeminiApiKey();
        return apiKey != null && !apiKey.isEmpty();
    }

    private String buildRequestBody(String prompt) throws Exception {
        String escapedPrompt = objectMapper.writeValueAsString(prompt);
        return String.format(
                "{\"contents\":[{\"parts\":[{\"text\":%s}]}]}",
                escapedPrompt
        );
    }

    private String extractResponseText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();
    }
}
