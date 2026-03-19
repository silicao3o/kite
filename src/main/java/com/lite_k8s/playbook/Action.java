package com.lite_k8s.playbook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Playbook 액션 스텝
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Action {

    /**
     * 액션 이름
     */
    private String name;

    /**
     * 액션 타입 (예: delay, container.restart, email, log)
     */
    private String type;

    /**
     * 액션 파라미터
     */
    private Map<String, String> params;

    /**
     * 조건부 실행 표현식 (예: {{restartCount}} < 3)
     */
    private String when;
}
