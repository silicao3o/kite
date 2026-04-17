package com.lite_k8s.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ImageUpdateHistoryEntityTest {

    @Test
    @DisplayName("10. ImageUpdateHistoryEntity는 watchId, image, tag, previousDigest, newDigest 필드를 가진다")
    void hasDigestFields() {
        ImageUpdateHistoryEntity entity = ImageUpdateHistoryEntity.builder()
                .watchId("watch-1")
                .image("ghcr.io/org/app")
                .tag("latest")
                .previousDigest("sha256:aaa")
                .newDigest("sha256:bbb")
                .build();

        assertThat(entity.getWatchId()).isEqualTo("watch-1");
        assertThat(entity.getImage()).isEqualTo("ghcr.io/org/app");
        assertThat(entity.getTag()).isEqualTo("latest");
        assertThat(entity.getPreviousDigest()).isEqualTo("sha256:aaa");
        assertThat(entity.getNewDigest()).isEqualTo("sha256:bbb");
    }

    @Test
    @DisplayName("11. ImageUpdateHistoryEntity는 status, nodeName, containerName 필드를 가진다")
    void hasStatusAndNodeFields() {
        ImageUpdateHistoryEntity entity = ImageUpdateHistoryEntity.builder()
                .watchId("watch-1")
                .image("ghcr.io/org/app")
                .status(ImageUpdateHistoryEntity.Status.DETECTED)
                .nodeName("worker-1")
                .containerName("app-1")
                .build();

        assertThat(entity.getStatus()).isEqualTo(ImageUpdateHistoryEntity.Status.DETECTED);
        assertThat(entity.getNodeName()).isEqualTo("worker-1");
        assertThat(entity.getContainerName()).isEqualTo("app-1");
    }

    @Test
    @DisplayName("12. ImageUpdateHistoryEntity는 createdAt, message 필드를 가진다")
    void hasCreatedAtAndMessage() {
        ImageUpdateHistoryEntity entity = ImageUpdateHistoryEntity.builder()
                .watchId("watch-1")
                .image("ghcr.io/org/app")
                .message("업데이트 성공")
                .build();

        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getMessage()).isEqualTo("업데이트 성공");
    }
}
