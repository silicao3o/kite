package com.lite_k8s.update;

import lombok.Builder;
import lombok.Getter;

/**
 * 컨테이너 업데이트 결과
 */
@Getter
@Builder
public class UpdateResult {

    private final String containerId;
    private final String containerName;
    private final String oldDigest;
    private final String newDigest;
    private final boolean success;
    private final String errorMessage;

    public static UpdateResult success(String containerId, String containerName,
                                       String oldDigest, String newDigest) {
        return UpdateResult.builder()
                .containerId(containerId)
                .containerName(containerName)
                .oldDigest(oldDigest)
                .newDigest(newDigest)
                .success(true)
                .build();
    }

    public static UpdateResult failure(String containerId, String containerName,
                                        String oldDigest, String newDigest, String errorMessage) {
        return UpdateResult.builder()
                .containerId(containerId)
                .containerName(containerName)
                .oldDigest(oldDigest)
                .newDigest(newDigest)
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
