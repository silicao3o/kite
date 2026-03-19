package com.lite_k8s.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageUpdateDetectedEventTest {

    @Test
    @DisplayName("ImageUpdateDetectedEvent는 nodeId를 포함한다")
    void shouldIncludeNodeId() {
        ImageUpdateDetectedEvent event = new ImageUpdateDetectedEvent(
                "c123", "my-app", "ghcr.io/foo/bar", "latest",
                "sha256:old", "sha256:new", null, "node-vm-b"
        );

        assertThat(event.getNodeId()).isEqualTo("node-vm-b");
    }

    @Test
    @DisplayName("nodeId가 없으면 null이다 (로컬 단일 모드 호환)")
    void shouldAllowNullNodeId() {
        ImageUpdateDetectedEvent event = new ImageUpdateDetectedEvent(
                "c123", "my-app", "ghcr.io/foo/bar", "latest",
                "sha256:old", "sha256:new", null
        );

        assertThat(event.getNodeId()).isNull();
    }
}
