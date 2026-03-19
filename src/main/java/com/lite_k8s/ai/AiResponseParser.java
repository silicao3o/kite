package com.lite_k8s.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 응답 파싱 유틸리티
 *
 * 모든 AI 클라이언트에서 공통으로 사용하는 응답 파싱 로직
 */
@Slf4j
public class AiResponseParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private AiResponseParser() {
        // 유틸리티 클래스
    }

    public static ClaudeResponse parse(String response) {
        if (response == null || response.isEmpty()) {
            return ClaudeResponse.error("Empty response");
        }

        String jsonContent = extractJson(response);
        if (jsonContent != null) {
            try {
                JsonNode node = objectMapper.readTree(jsonContent);

                String action = getTextOrNull(node, "action");
                String reasoning = getTextOrNull(node, "reasoning");
                String riskLevel = getTextOrNull(node, "riskLevel");
                double confidence = node.has("confidence") ? node.get("confidence").asDouble() : 0.0;

                return ClaudeResponse.success(action, reasoning, riskLevel, confidence, response);

            } catch (Exception e) {
                log.debug("Failed to parse JSON response: {}", e.getMessage());
            }
        }

        return ClaudeResponse.text(response);
    }

    public static String extractJson(String response) {
        int start = response.indexOf("```json");
        if (start >= 0) {
            start = response.indexOf("\n", start) + 1;
            int end = response.indexOf("```", start);
            if (end > start) {
                return response.substring(start, end).trim();
            }
        }

        int braceStart = response.indexOf("{");
        int braceEnd = response.lastIndexOf("}");
        if (braceStart >= 0 && braceEnd > braceStart) {
            return response.substring(braceStart, braceEnd + 1);
        }

        return null;
    }

    private static String getTextOrNull(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText() : null;
    }
}
