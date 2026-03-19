package com.lite_k8s.ai;

import lombok.Builder;
import lombok.Data;

/**
 * Claude Code 응답
 */
@Data
@Builder
public class ClaudeResponse {

    // 파싱된 필드 (JSON 응답인 경우)
    private String action;         // restart, kill, scale, notify 등
    private String reasoning;      // AI 판단 이유
    private String riskLevel;      // LOW, MEDIUM, HIGH, CRITICAL
    private double confidence;     // 신뢰도 (0.0 ~ 1.0)

    // 원본 응답
    private String rawResponse;
    private boolean jsonParsed;    // JSON 파싱 성공 여부

    // 에러
    private boolean error;
    private String errorMessage;

    /**
     * 성공 응답 생성 (JSON 파싱됨)
     */
    public static ClaudeResponse success(String action, String reasoning,
                                          String riskLevel, double confidence,
                                          String rawResponse) {
        return ClaudeResponse.builder()
                .action(action)
                .reasoning(reasoning)
                .riskLevel(riskLevel)
                .confidence(confidence)
                .rawResponse(rawResponse)
                .jsonParsed(true)
                .error(false)
                .build();
    }

    /**
     * 텍스트 응답 생성 (JSON 파싱 실패)
     */
    public static ClaudeResponse text(String rawResponse) {
        return ClaudeResponse.builder()
                .rawResponse(rawResponse)
                .jsonParsed(false)
                .error(false)
                .build();
    }

    /**
     * 에러 응답 생성
     */
    public static ClaudeResponse error(String errorMessage) {
        return ClaudeResponse.builder()
                .error(true)
                .errorMessage(errorMessage)
                .jsonParsed(false)
                .build();
    }
}
