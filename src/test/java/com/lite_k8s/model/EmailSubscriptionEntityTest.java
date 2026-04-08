package com.lite_k8s.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailSubscriptionEntityTest {

    @Test
    void shouldCreateWithDefaults() {
        EmailSubscriptionEntity entity = EmailSubscriptionEntity.builder()
                .email("alice@example.com")
                .containerPattern("web-*")
                .enabled(true)
                .build();

        assertThat(entity.getId()).isNotBlank();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getEmail()).isEqualTo("alice@example.com");
        assertThat(entity.getContainerPattern()).isEqualTo("web-*");
        assertThat(entity.getNodeName()).isNull();
        assertThat(entity.isNotifyIntentional()).isFalse();
        assertThat(entity.isEnabled()).isTrue();
    }

    @Test
    void shouldAcceptNodeNameOnly() {
        EmailSubscriptionEntity entity = EmailSubscriptionEntity.builder()
                .email("bob@example.com")
                .nodeName("worker-1")
                .enabled(true)
                .build();

        entity.validate();

        assertThat(entity.getContainerPattern()).isNull();
        assertThat(entity.getNodeName()).isEqualTo("worker-1");
    }

    @Test
    void shouldAcceptBothContainerPatternAndNodeName() {
        EmailSubscriptionEntity entity = EmailSubscriptionEntity.builder()
                .email("charlie@example.com")
                .containerPattern("api-*")
                .nodeName("worker-2")
                .notifyIntentional(true)
                .enabled(true)
                .build();

        entity.validate();

        assertThat(entity.getContainerPattern()).isEqualTo("api-*");
        assertThat(entity.getNodeName()).isEqualTo("worker-2");
        assertThat(entity.isNotifyIntentional()).isTrue();
    }

    @Test
    void validate_shouldThrowWhenBothPatternAndNodeAreNull() {
        EmailSubscriptionEntity entity = EmailSubscriptionEntity.builder()
                .email("invalid@example.com")
                .enabled(true)
                .build();

        assertThatThrownBy(entity::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("containerPattern");
    }

    @Test
    void validate_shouldThrowWhenBothAreBlank() {
        EmailSubscriptionEntity entity = EmailSubscriptionEntity.builder()
                .email("invalid@example.com")
                .containerPattern("")
                .nodeName("  ")
                .enabled(true)
                .build();

        assertThatThrownBy(entity::validate)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_shouldThrowWhenEmailIsBlank() {
        EmailSubscriptionEntity entity = EmailSubscriptionEntity.builder()
                .email("  ")
                .containerPattern("web-*")
                .enabled(true)
                .build();

        assertThatThrownBy(entity::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email");
    }
}
