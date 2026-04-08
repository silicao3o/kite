package com.lite_k8s.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerDeathEventIntentionalTest {

    @Test
    void shouldDefaultIntentionalToFalse() {
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerId("abc123")
                .containerName("web-1")
                .build();

        assertThat(event.isIntentional()).isFalse();
        assertThat(event.getIntentionalReason()).isNull();
    }

    @Test
    void shouldSetIntentionalAndReason() {
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerId("abc123")
                .intentional(true)
                .intentionalReason("stop-event-precedent")
                .build();

        assertThat(event.isIntentional()).isTrue();
        assertThat(event.getIntentionalReason()).isEqualTo("stop-event-precedent");
    }
}
