package com.lite_k8s.ai;

import com.lite_k8s.playbook.Action;
import com.lite_k8s.playbook.RiskLevel;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * AI 자유 판단 결과
 */
@Data
@Builder
public class AiJudgmentResult {

    /**
     * AI가 추천한 액션 목록
     */
    private List<Action> actions;

    /**
     * AI 판단 이유
     */
    private String reasoning;

    /**
     * 위험도 레벨
     */
    private RiskLevel riskLevel;

    /**
     * AI 신뢰도 (0.0 ~ 1.0)
     */
    private double confidence;

    /**
     * 원본 AI 응답
     */
    private ClaudeResponse rawResponse;
}
