package com.lite_k8s.playbook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Playbook 실행 결과
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybookResult {

    private boolean success;
    private String playbookName;
    private String error;

    @Builder.Default
    private List<ActionResult> actionResults = new ArrayList<>();

    public static PlaybookResult success(String playbookName, List<ActionResult> actionResults) {
        return PlaybookResult.builder()
                .success(true)
                .playbookName(playbookName)
                .actionResults(actionResults)
                .build();
    }

    public static PlaybookResult failure(String playbookName, String error, List<ActionResult> actionResults) {
        return PlaybookResult.builder()
                .success(false)
                .playbookName(playbookName)
                .error(error)
                .actionResults(actionResults)
                .build();
    }
}
