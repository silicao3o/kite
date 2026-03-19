package com.lite_k8s.playbook;

import java.util.Map;

/**
 * 액션 핸들러 인터페이스
 *
 * 각 액션 타입별로 구현체 필요
 */
public interface ActionHandler {

    /**
     * 액션 실행
     *
     * @param action 실행할 액션
     * @param context 컨텍스트 데이터 (변수 값 등)
     * @return 실행 결과
     */
    ActionResult execute(Action action, Map<String, String> context);
}
