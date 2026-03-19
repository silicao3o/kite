package com.lite_k8s.incident;

import com.lite_k8s.ai.ClaudeCodeClient;
import com.lite_k8s.ai.ClaudeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final SuggestionRepository repository;
    private final ClaudeCodeClient claudeCodeClient;

    public Suggestion generatePlaybookSuggestion(String playbookName, IncidentPattern pattern) {
        String content = buildPlaybookContent(playbookName, pattern);

        Suggestion suggestion = Suggestion.builder()
                .containerName(pattern.getContainerName())
                .type(Suggestion.Type.PLAYBOOK_IMPROVEMENT)
                .content(content)
                .patternOccurrenceCount(pattern.getOccurrenceCount())
                .build();

        repository.save(suggestion);
        log.info("Playbook 개선 제안 생성: playbook={}, container={}", playbookName, pattern.getContainerName());
        return suggestion;
    }

    public Suggestion generateFromPattern(IncidentPattern pattern) {
        String content = buildContent(pattern);

        Suggestion suggestion = Suggestion.builder()
                .containerName(pattern.getContainerName())
                .type(Suggestion.Type.CONFIG_OPTIMIZATION)
                .content(content)
                .patternOccurrenceCount(pattern.getOccurrenceCount())
                .build();

        repository.save(suggestion);
        log.info("제안 생성: container={}, occurrences={}", pattern.getContainerName(), pattern.getOccurrenceCount());
        return suggestion;
    }

    public void approve(String suggestionId) {
        updateStatus(suggestionId, Suggestion.Status.APPROVED);
    }

    public void reject(String suggestionId) {
        updateStatus(suggestionId, Suggestion.Status.REJECTED);
    }

    public List<Suggestion> findAll() {
        return repository.findAll();
    }

    public List<Suggestion> findPending() {
        return repository.findByStatus(Suggestion.Status.PENDING);
    }

    public Optional<Suggestion> findById(String id) {
        return repository.findById(id);
    }

    private String buildContent(IncidentPattern pattern) {
        if (claudeCodeClient.isEnabled()) {
            return buildContentWithAi(pattern);
        }
        return buildDefaultContent(pattern);
    }

    private String buildContentWithAi(IncidentPattern pattern) {
        try {
            String prompt = buildPrompt(pattern);
            ClaudeResponse response = claudeCodeClient.analyzeWithPrompt(prompt);

            if (!response.isError() && response.isJsonParsed() && response.getReasoning() != null) {
                return response.getReasoning();
            }
        } catch (Exception e) {
            log.warn("AI 제안 생성 실패: {}", e.getMessage());
        }
        return buildDefaultContent(pattern);
    }

    private String buildPlaybookContent(String playbookName, IncidentPattern pattern) {
        if (claudeCodeClient.isEnabled()) {
            try {
                String prompt = buildPlaybookPrompt(playbookName, pattern);
                ClaudeResponse response = claudeCodeClient.analyzeWithPrompt(prompt);
                if (!response.isError() && response.isJsonParsed() && response.getReasoning() != null) {
                    return response.getReasoning();
                }
            } catch (Exception e) {
                log.warn("AI Playbook 제안 생성 실패: {}", e.getMessage());
            }
        }
        return String.format(
                "[%s] Playbook '%s'이 %d회 실행되었으나 장애가 반복됩니다. " +
                "Playbook의 액션 시퀀스와 딜레이 설정을 검토하세요.",
                pattern.getContainerName(), playbookName, pattern.getOccurrenceCount()
        );
    }

    private String buildPlaybookPrompt(String playbookName, IncidentPattern pattern) {
        return """
                Playbook 개선 제안을 요청합니다.

                ## 상황
                - 컨테이너: %s
                - 적용된 Playbook: %s
                - 24시간 내 반복 횟수: %d회
                - 장애 내용: %s

                Playbook '%s'의 개선 방안을 제안해주세요.

                JSON 형식으로 응답:
                ```json
                {
                    "action": "notify",
                    "reasoning": "Playbook 개선 제안",
                    "riskLevel": "LOW",
                    "confidence": 0.85
                }
                ```
                """.formatted(
                pattern.getContainerName(), playbookName,
                pattern.getOccurrenceCount(), pattern.getCommonSummary(),
                playbookName
        );
    }

    private String buildDefaultContent(IncidentPattern pattern) {
        return String.format(
                "[%s] 최근 24시간 내 %d회 장애가 반복되었습니다. " +
                "마지막 장애 내용: '%s'. " +
                "메모리/CPU 한도 설정을 검토하고, 로그를 분석하여 근본 원인을 파악하세요.",
                pattern.getContainerName(),
                pattern.getOccurrenceCount(),
                pattern.getCommonSummary()
        );
    }

    private String buildPrompt(IncidentPattern pattern) {
        return """
                컨테이너 반복 장애 패턴이 감지되었습니다. 설정 최적화 제안을 작성해주세요.

                ## 패턴 정보
                - 컨테이너: %s
                - 24시간 내 장애 횟수: %d회
                - 마지막 장애 내용: %s
                - 첫 번째 발생: %s
                - 마지막 발생: %s

                ## 요청
                이 패턴을 해결하기 위한 구체적인 설정 최적화 제안을 작성해주세요.
                (예: 메모리 한도 증설, CPU 제한 조정, 재시작 정책 변경 등)

                JSON 형식으로 응답:
                ```json
                {
                    "action": "notify",
                    "reasoning": "구체적인 설정 최적화 제안",
                    "riskLevel": "LOW",
                    "confidence": 0.9
                }
                ```
                """.formatted(
                pattern.getContainerName(),
                pattern.getOccurrenceCount(),
                pattern.getCommonSummary(),
                pattern.getFirstOccurrence(),
                pattern.getLastOccurrence()
        );
    }

    private void updateStatus(String suggestionId, Suggestion.Status status) {
        repository.findById(suggestionId).ifPresent(s -> {
            s.setStatus(status);
            repository.save(s);
        });
    }
}
