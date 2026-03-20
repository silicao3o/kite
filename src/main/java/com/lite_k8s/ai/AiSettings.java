package com.lite_k8s.ai;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI 설정 영속성 엔티티 (단일 행)
 */
@Entity
@Table(name = "ai_settings")
@Getter
@Setter
@NoArgsConstructor
public class AiSettings {

    private static final String DEFAULT_ID = "default";

    @Id
    private String id = DEFAULT_ID;

    private boolean enabled;

    @Enumerated(EnumType.STRING)
    private AiProvider provider = AiProvider.ANTHROPIC;

    private String anthropicApiKey = "";
    private String openaiApiKey = "";
    private String geminiApiKey = "";

    private String anthropicModel = "claude-haiku-4-5-20251001";
    private String openaiModel = "gpt-4o-mini";
    private String geminiModel = "gemini-2.0-flash";

    public static AiSettings defaultSettings() {
        return new AiSettings();
    }
}
