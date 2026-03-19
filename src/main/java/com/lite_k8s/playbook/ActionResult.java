package com.lite_k8s.playbook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 액션 실행 결과
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionResult {

    private boolean success;
    private String error;
    private Object data;

    public static ActionResult success() {
        return ActionResult.builder().success(true).build();
    }

    public static ActionResult success(Object data) {
        return ActionResult.builder().success(true).data(data).build();
    }

    public static ActionResult failure(String error) {
        return ActionResult.builder().success(false).error(error).build();
    }
}
