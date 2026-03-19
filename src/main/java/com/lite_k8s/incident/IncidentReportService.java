package com.lite_k8s.incident;

import com.lite_k8s.ai.AiClientSelector;
import com.lite_k8s.ai.ClaudeResponse;
import com.lite_k8s.model.ContainerDeathEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentReportService {

    private final IncidentReportRepository repository;
    private final AiClientSelector aiClientSelector;

    public IncidentReport createReport(ContainerDeathEvent event) {
        String summary = buildSummary(event);

        IncidentReport report = IncidentReport.builder()
                .containerId(event.getContainerId())
                .containerName(event.getContainerName())
                .summary(summary)
                .build();

        if (aiClientSelector.isEnabled()) {
            report.setStatus(IncidentReport.Status.ANALYZING);
            analyzeWithAi(report, event);
        }

        repository.save(report);
        return report;
    }

    public void closeReport(String reportId, String rootCause, List<String> suggestions) {
        Optional<IncidentReport> found = repository.findById(reportId);
        if (found.isEmpty()) {
            log.warn("IncidentReport not found: {}", reportId);
            return;
        }

        IncidentReport report = found.get();
        report.setRootCause(rootCause);
        report.setSuggestions(suggestions);
        report.setStatus(IncidentReport.Status.CLOSED);
        report.setClosedAt(LocalDateTime.now());
        repository.save(report);
    }

    public List<IncidentReport> findAll() {
        return repository.findAll();
    }

    public List<IncidentReport> findByContainerName(String containerName) {
        return repository.findByContainerName(containerName);
    }

    public Optional<IncidentReport> findById(String id) {
        return repository.findById(id);
    }

    private String buildSummary(ContainerDeathEvent event) {
        if (event.isOomKilled()) {
            return String.format("[%s] OOM(메모리 부족)으로 컨테이너가 종료되었습니다", event.getContainerName());
        }
        if (event.getExitCode() != null && event.getExitCode() != 0) {
            return String.format("[%s] 비정상 종료되었습니다 (exit code: %d)", event.getContainerName(), event.getExitCode());
        }
        return String.format("[%s] 컨테이너가 종료되었습니다", event.getContainerName());
    }

    private void analyzeWithAi(IncidentReport report, ContainerDeathEvent event) {
        try {
            String prompt = buildIncidentPrompt(event);
            ClaudeResponse response = aiClientSelector.analyzeWithPrompt(prompt);

            if (!response.isError() && response.isJsonParsed()) {
                report.setRootCause(response.getReasoning());
                log.info("AI incident analysis completed for {}: rootCause={}",
                        event.getContainerName(), response.getReasoning());
            }
        } catch (Exception e) {
            log.error("AI incident analysis failed for {}: {}", event.getContainerName(), e.getMessage());
        }
    }

    private String buildIncidentPrompt(ContainerDeathEvent event) {
        return """
                컨테이너 장애 사후 분석 요청입니다.

                ## 장애 정보
                - 컨테이너: %s (ID: %s)
                - 종료 시각: %s
                - Exit Code: %s
                - OOM 종료: %s
                - 종료 원인: %s

                ## 마지막 로그
                ```
                %s
                ```

                ## 분석 요청
                위 정보를 바탕으로 장애 근본 원인을 분석하고 재발 방지 제안을 해주세요.

                JSON 형식으로 응답:
                ```json
                {
                    "action": "notify",
                    "reasoning": "근본 원인 분석",
                    "riskLevel": "HIGH",
                    "confidence": 0.9
                }
                ```
                """.formatted(
                event.getContainerName(),
                event.getContainerId(),
                event.getDeathTime(),
                event.getExitCode(),
                event.isOomKilled() ? "예" : "아니오",
                event.getDeathReason() != null ? event.getDeathReason() : "알 수 없음",
                event.getLastLogs() != null ? event.getLastLogs() : "(없음)"
        );
    }
}
