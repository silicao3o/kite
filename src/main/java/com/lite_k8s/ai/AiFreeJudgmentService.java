package com.lite_k8s.ai;

import com.lite_k8s.model.ContainerDeathEvent;
import com.lite_k8s.playbook.Action;
import com.lite_k8s.playbook.RiskLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * AI 자유 판단 서비스
 *
 * Playbook이 등록되지 않은 이벤트에 대해 AI가 직접 판단
 */
@Slf4j
@Service
public class AiFreeJudgmentService {

    private final ClaudeCodeClient claudeCodeClient;
    private final AnomalyContextBuilder contextBuilder;
    private final AiResponseConverter responseConverter;
    private final double confidenceThreshold;

    public AiFreeJudgmentService(
            ClaudeCodeClient claudeCodeClient,
            AnomalyContextBuilder contextBuilder,
            AiResponseConverter responseConverter,
            @Value("${docker.monitor.ai.confidence-threshold:0.6}") double confidenceThreshold) {
        this.claudeCodeClient = claudeCodeClient;
        this.contextBuilder = contextBuilder;
        this.responseConverter = responseConverter;
        this.confidenceThreshold = confidenceThreshold;
    }

    /**
     * 이벤트에 대해 AI 판단 수행
     *
     * @param event 컨테이너 종료 이벤트
     * @return AI 판단 결과 (판단 불가 시 empty)
     */
    public Optional<AiJudgmentResult> judge(ContainerDeathEvent event) {
        if (!claudeCodeClient.isEnabled()) {
            log.debug("AI judgment skipped: AI is disabled");
            return Optional.empty();
        }

        try {
            // 컨텍스트 빌드
            AnomalyContext context = contextBuilder
                    .fromEvent(event)
                    .build();

            // AI 분석 요청
            ClaudeResponse response = claudeCodeClient.analyze(context);

            // 에러 체크
            if (response.isError()) {
                log.warn("AI judgment failed: {}", response.getErrorMessage());
                return Optional.empty();
            }

            // 신뢰도 체크
            if (response.getConfidence() < confidenceThreshold) {
                log.info("AI judgment skipped: confidence {} below threshold {}",
                        response.getConfidence(), confidenceThreshold);
                return Optional.empty();
            }

            // 액션 변환
            String containerId = event.getContainerId();
            List<Action> actions = responseConverter.convert(response, containerId);

            // 액션이 없으면 (ignore 등) 빈 결과
            if (actions.isEmpty()) {
                log.debug("AI judgment: no action required for container {}",
                        event.getContainerName());
                return Optional.empty();
            }

            // 위험도 변환
            RiskLevel riskLevel = responseConverter.toRiskLevel(response.getRiskLevel());

            log.info("AI judgment for {}: action={}, risk={}, confidence={}",
                    event.getContainerName(),
                    response.getAction(),
                    riskLevel,
                    response.getConfidence());

            return Optional.of(AiJudgmentResult.builder()
                    .actions(actions)
                    .reasoning(response.getReasoning())
                    .riskLevel(riskLevel)
                    .confidence(response.getConfidence())
                    .rawResponse(response)
                    .build());

        } catch (Exception e) {
            log.error("AI judgment error for container {}: {}",
                    event.getContainerName(), e.getMessage(), e);
            return Optional.empty();
        }
    }
}
