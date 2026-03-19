package com.lite_k8s.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class HealingEventTest {

    @Test
    void shouldCreateHealingEvent() {
        LocalDateTime now = LocalDateTime.now();

        HealingEvent event = HealingEvent.builder()
                .containerId("abc123")
                .containerName("web-server")
                .timestamp(now)
                .success(true)
                .restartCount(1)
                .message("자가치유 성공")
                .build();

        assertThat(event.getContainerId()).isEqualTo("abc123");
        assertThat(event.getContainerName()).isEqualTo("web-server");
        assertThat(event.getTimestamp()).isEqualTo(now);
        assertThat(event.isSuccess()).isTrue();
        assertThat(event.getRestartCount()).isEqualTo(1);
        assertThat(event.getMessage()).isEqualTo("자가치유 성공");
    }
}
