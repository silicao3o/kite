package com.lite_k8s.ai;

public interface AiClient {
    ClaudeResponse analyzeWithPrompt(String prompt);
    boolean isEnabled();
}
