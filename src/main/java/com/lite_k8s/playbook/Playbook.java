package com.lite_k8s.playbook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 자가치유 Playbook
 *
 * 특정 이벤트 발생 시 실행할 액션들을 정의
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Playbook {

    /**
     * Playbook 이름 (고유 식별자)
     */
    private String name;

    /**
     * Playbook 설명
     */
    private String description;

    /**
     * 트리거 조건
     */
    private Trigger trigger;

    /**
     * 실행할 액션 목록 (순서대로 실행)
     */
    private List<Action> actions;

    /**
     * 위험도 레벨
     */
    @Builder.Default
    private RiskLevel riskLevel = RiskLevel.LOW;
}
