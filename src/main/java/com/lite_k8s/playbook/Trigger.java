package com.lite_k8s.playbook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Playbook 트리거 조건
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trigger {

    /**
     * 트리거 이벤트 타입 (예: container.die, container.oom, metrics.cpu.high)
     */
    private String event;

    /**
     * 추가 조건 (예: exitCode=137, oomKilled=true)
     */
    private Map<String, String> conditions;
}
