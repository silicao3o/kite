package com.lite_k8s.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Claude Code CLI 클라이언트
 *
 * Claude Code CLI를 호출하여 AI 판단을 요청
 */
@Slf4j
@Component
public class ClaudeCodeClient implements AiClient {

    private static final String CLAUDE_COMMAND = "claude";
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${docker.monitor.ai.timeout-seconds:60}")
    private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    @Value("${docker.monitor.ai.enabled:false}")
    private boolean enabled = false;

    /**
     * 이상 컨텍스트를 기반으로 AI 분석 요청
     */
    public ClaudeResponse analyze(AnomalyContext context) {
        if (!enabled) {
            log.debug("AI analysis disabled");
            return ClaudeResponse.error("AI analysis is disabled");
        }

        try {
            String prompt = buildPrompt(context);
            String[] command = buildCommand(prompt);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Claude Code process timed out");
                return ClaudeResponse.error("Process timed out after " + timeoutSeconds + " seconds");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.warn("Claude Code process exited with code: {}", exitCode);
                return ClaudeResponse.error("Process exited with code: " + exitCode);
            }

            return parseResponse(output.toString().trim());

        } catch (Exception e) {
            log.error("Failed to execute Claude Code", e);
            return ClaudeResponse.error("Execution failed: " + e.getMessage());
        }
    }

    /**
     * 프롬프트 생성
     */
    public String buildPrompt(AnomalyContext context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an AI operations assistant analyzing a container anomaly.\n\n");

        prompt.append("## Container Information\n");
        prompt.append("- Name: ").append(context.getContainerName()).append("\n");
        prompt.append("- ID: ").append(context.getContainerId()).append("\n");
        if (context.getImageName() != null) {
            prompt.append("- Image: ").append(context.getImageName()).append("\n");
        }
        prompt.append("\n");

        prompt.append("## Event Details\n");
        prompt.append("- Event Type: ").append(context.getEventType()).append("\n");
        prompt.append("- Exit Code: ").append(context.getExitCode()).append("\n");
        prompt.append("- OOM Killed: ").append(context.isOomKilled() ? "Yes" : "No").append("\n");
        prompt.append("\n");

        prompt.append("## Current Metrics\n");
        prompt.append("- CPU Usage: ").append(String.format("%.1f%%", context.getCpuPercent())).append("\n");
        prompt.append("- Memory Usage: ").append(String.format("%.1f%%", context.getMemoryPercent())).append("\n");
        if (context.getMemoryUsage() > 0 && context.getMemoryLimit() > 0) {
            prompt.append("- Memory: ").append(formatBytes(context.getMemoryUsage()))
                    .append(" / ").append(formatBytes(context.getMemoryLimit())).append("\n");
        }
        prompt.append("- Restart Count: ").append(context.getRestartCount()).append("\n");
        prompt.append("\n");

        if (context.getRecentLogs() != null && !context.getRecentLogs().isEmpty()) {
            prompt.append("## Recent Logs\n```\n");
            prompt.append(context.getRecentLogs());
            prompt.append("\n```\n\n");
        }

        prompt.append("## Task\n");
        prompt.append("Analyze this container issue and provide a recommendation.\n\n");

        prompt.append("Respond in JSON format:\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"action\": \"restart|kill|scale|notify|ignore\",\n");
        prompt.append("  \"reasoning\": \"Your detailed analysis\",\n");
        prompt.append("  \"riskLevel\": \"LOW|MEDIUM|HIGH|CRITICAL\",\n");
        prompt.append("  \"confidence\": 0.0-1.0\n");
        prompt.append("}\n");
        prompt.append("```\n");

        return prompt.toString();
    }

    /**
     * CLI 명령어 생성
     */
    public String[] buildCommand(String prompt) {
        return new String[]{
                CLAUDE_COMMAND,
                "-p", prompt,
                "--output-format", "text"
        };
    }

    /**
     * 응답 파싱
     */
    public ClaudeResponse parseResponse(String response) {
        if (response == null || response.isEmpty()) {
            return ClaudeResponse.error("Empty response");
        }

        // JSON 블록 추출 시도
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

        // JSON 파싱 실패 시 텍스트로 반환
        return ClaudeResponse.text(response);
    }

    private String extractJson(String response) {
        // ```json ... ``` 블록 추출
        int start = response.indexOf("```json");
        if (start >= 0) {
            start = response.indexOf("\n", start) + 1;
            int end = response.indexOf("```", start);
            if (end > start) {
                return response.substring(start, end).trim();
            }
        }

        // { ... } 블록 추출
        int braceStart = response.indexOf("{");
        int braceEnd = response.lastIndexOf("}");
        if (braceStart >= 0 && braceEnd > braceStart) {
            return response.substring(braceStart, braceEnd + 1);
        }

        return null;
    }

    private String getTextOrNull(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText() : null;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * 커스텀 프롬프트로 AI 분석 요청
     */
    public ClaudeResponse analyzeWithPrompt(String prompt) {
        if (!enabled) {
            log.debug("AI analysis disabled");
            return ClaudeResponse.error("AI analysis is disabled");
        }

        try {
            String[] command = buildCommand(prompt);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Claude Code process timed out");
                return ClaudeResponse.error("Process timed out after " + timeoutSeconds + " seconds");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.warn("Claude Code process exited with code: {}", exitCode);
                return ClaudeResponse.error("Process exited with code: " + exitCode);
            }

            return parseResponse(output.toString().trim());

        } catch (Exception e) {
            log.error("Failed to execute Claude Code", e);
            return ClaudeResponse.error("Execution failed: " + e.getMessage());
        }
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
