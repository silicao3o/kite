package com.lite_k8s.ai;

import com.lite_k8s.playbook.Action;
import com.lite_k8s.playbook.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AI 응답을 Playbook Action으로 변환
 */
@Component
public class AiResponseConverter {

    /**
     * ClaudeResponse를 Action 목록으로 변환
     */
    public List<Action> convert(ClaudeResponse response, String containerId) {
        if (response == null || response.isError() || !response.isJsonParsed()) {
            return Collections.emptyList();
        }

        String action = response.getAction();
        if (action == null || "ignore".equalsIgnoreCase(action)) {
            return Collections.emptyList();
        }

        Action converted = convertAction(action, containerId);
        return converted != null ? List.of(converted) : Collections.emptyList();
    }

    private Action convertAction(String actionType, String containerId) {
        return switch (actionType.toLowerCase()) {
            case "restart" -> Action.builder()
                    .name("ai-restart")
                    .type("container.restart")
                    .params(Map.of("containerId", containerId))
                    .build();

            case "kill" -> Action.builder()
                    .name("ai-kill")
                    .type("container.kill")
                    .params(Map.of("containerId", containerId))
                    .build();

            case "scale" -> Action.builder()
                    .name("ai-scale")
                    .type("container.scale")
                    .params(Map.of("containerId", containerId))
                    .build();

            case "notify" -> Action.builder()
                    .name("ai-notify")
                    .type("notify")
                    .params(Map.of("containerId", containerId))
                    .build();

            default -> null;
        };
    }

    /**
     * 문자열을 RiskLevel로 변환
     */
    public RiskLevel toRiskLevel(String riskLevel) {
        if (riskLevel == null) {
            return RiskLevel.MEDIUM;
        }

        return switch (riskLevel.toUpperCase()) {
            case "LOW" -> RiskLevel.LOW;
            case "MEDIUM" -> RiskLevel.MEDIUM;
            case "HIGH" -> RiskLevel.HIGH;
            case "CRITICAL" -> RiskLevel.CRITICAL;
            default -> RiskLevel.MEDIUM;
        };
    }
}
