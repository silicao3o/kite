package com.lite_k8s.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AI 로그 분석 서비스
 *
 * 컨테이너 로그를 AI에게 전달하여 원인 분석
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogAnalysisService {

    private final AiClientSelector aiClientSelector;

    /**
     * 로그 분석 요청
     *
     * @param containerId 컨테이너 ID
     * @param containerName 컨테이너 이름
     * @param logs 분석할 로그
     * @return 분석 결과 (분석 불가 시 empty)
     */
    public Optional<LogAnalysisResult> analyze(String containerId, String containerName, String logs) {
        if (!aiClientSelector.isEnabled()) {
            log.debug("AI log analysis skipped: AI is disabled");
            return Optional.empty();
        }

        if (logs == null || logs.trim().isEmpty()) {
            log.debug("AI log analysis skipped: empty logs");
            return Optional.empty();
        }

        try {
            String prompt = buildLogAnalysisPrompt(containerId, containerName, logs);
            ClaudeResponse response = aiClientSelector.analyzeWithPrompt(prompt);

            if (response.isError()) {
                log.warn("AI log analysis failed: {}", response.getErrorMessage());
                return Optional.empty();
            }

            if (!response.isJsonParsed()) {
                log.warn("AI log analysis failed: could not parse response");
                return Optional.empty();
            }

            LogAnalysisResult result = buildResult(containerId, containerName, response);
            log.info("AI log analysis completed for {}: severity={}, confidence={}",
                    containerName, result.getSeverity(), result.getConfidence());

            return Optional.of(result);

        } catch (Exception e) {
            log.error("AI log analysis error for container {}: {}", containerName, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private String buildLogAnalysisPrompt(String containerId, String containerName, String logs) {
        return """
            You are an AI operations assistant analyzing container logs.

            ## Container Information
            - Name: %s
            - ID: %s

            ## Logs to Analyze
            ```
            %s
            ```

            ## Task
            Analyze these logs and identify:
            1. Root cause of any errors or issues
            2. Severity level
            3. Recommended actions

            Respond in JSON format:
            ```json
            {
                "action": "restart|kill|scale|notify|ignore",
                "reasoning": "Detailed root cause analysis",
                "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
                "confidence": 0.0-1.0
            }
            ```
            """.formatted(containerName, containerId, truncateLogs(logs));
    }

    private String truncateLogs(String logs) {
        int maxLength = 5000;
        if (logs.length() <= maxLength) {
            return logs;
        }
        return "... (truncated)\n" + logs.substring(logs.length() - maxLength);
    }

    private LogAnalysisResult buildResult(String containerId, String containerName, ClaudeResponse response) {
        List<String> suggestedActions = new ArrayList<>();
        if (response.getAction() != null && !response.getAction().equals("ignore")) {
            suggestedActions.add(response.getAction());
        }

        return LogAnalysisResult.builder()
                .containerId(containerId)
                .containerName(containerName)
                .rootCause(response.getReasoning())
                .severity(response.getRiskLevel())
                .suggestedActions(suggestedActions)
                .confidence(response.getConfidence())
                .analyzedAt(LocalDateTime.now())
                .rawResponse(response)
                .build();
    }
}
