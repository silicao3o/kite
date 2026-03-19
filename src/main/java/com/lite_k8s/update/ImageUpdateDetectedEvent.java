package com.lite_k8s.update;

import lombok.Getter;

/**
 * 이미지 새 버전 감지 이벤트
 * ImageUpdatePoller가 발행하고 RollingUpdateService가 수신
 */
@Getter
public class ImageUpdateDetectedEvent {

    private final String containerId;
    private final String containerName;
    private final String imageName;
    private final String tag;
    private final String currentDigest;
    private final String newDigest;
    private final ImageWatchProperties.ImageWatch watch;
    private final String nodeId; // null = 로컬 단일 모드

    public ImageUpdateDetectedEvent(
            String containerId,
            String containerName,
            String imageName,
            String tag,
            String currentDigest,
            String newDigest,
            ImageWatchProperties.ImageWatch watch) {
        this(containerId, containerName, imageName, tag, currentDigest, newDigest, watch, null);
    }

    public ImageUpdateDetectedEvent(
            String containerId,
            String containerName,
            String imageName,
            String tag,
            String currentDigest,
            String newDigest,
            ImageWatchProperties.ImageWatch watch,
            String nodeId) {
        this.containerId = containerId;
        this.containerName = containerName;
        this.imageName = imageName;
        this.tag = tag;
        this.currentDigest = currentDigest;
        this.newDigest = newDigest;
        this.watch = watch;
        this.nodeId = nodeId;
    }
}
