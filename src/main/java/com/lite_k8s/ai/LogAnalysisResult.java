package com.lite_k8s.ai;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 로그 분석 결과
 */
@Data
@Builder
public class LogAnalysisResult {

    /**
     * 분석된 컨테이너 ID
     */
    private String containerId;

    /**
     * 분석된 컨테이너 이름
     */
    private String containerName;

    /**
     * 근본 원인 분석
     */
    private String rootCause;

    /**
     * 심각도 (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String severity;

    /**
     * 권장 조치 목록
     */
    private List<String> suggestedActions;

    /**
     * AI 신뢰도
     */
    private double confidence;

    /**
     * 분석 시간
     */
    private LocalDateTime analyzedAt;

    /**
     * 원본 AI 응답
     */
    private ClaudeResponse rawResponse;
}
