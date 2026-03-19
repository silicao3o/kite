package com.lite_k8s.deploy;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DeployResult {

    private final boolean success;
    private final DeploymentType strategy;
    private final int deployed;
    private final int failed;
    private final String message;
    private final List<String> deployedContainerIds;

    public static DeployResult success(DeploymentType strategy, int deployed,
                                        List<String> ids, String message) {
        return DeployResult.builder()
                .success(true)
                .strategy(strategy)
                .deployed(deployed)
                .failed(0)
                .message(message)
                .deployedContainerIds(ids)
                .build();
    }

    public static DeployResult failure(DeploymentType strategy, int deployed, int failed, String message) {
        return DeployResult.builder()
                .success(false)
                .strategy(strategy)
                .deployed(deployed)
                .failed(failed)
                .message(message)
                .deployedContainerIds(List.of())
                .build();
    }
}
