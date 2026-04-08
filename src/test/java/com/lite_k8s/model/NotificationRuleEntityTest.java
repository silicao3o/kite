package com.lite_k8s.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRuleEntityTest {

    @Test
    void shouldCreateEntityWithDefaults() {
        NotificationRuleEntity entity = NotificationRuleEntity.builder()
                .namePattern("web-*")
                .mode(NotificationRuleEntity.Mode.INCLUDE)
                .enabled(true)
                .build();

        assertThat(entity.getId()).isNotBlank();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getNamePattern()).isEqualTo("web-*");
        assertThat(entity.getMode()).isEqualTo(NotificationRuleEntity.Mode.INCLUDE);
        assertThat(entity.isEnabled()).isTrue();
        assertThat(entity.isNotifyIntentional()).isFalse(); // default
    }

    @Test
    void shouldSetAllFields() {
        NotificationRuleEntity entity = NotificationRuleEntity.builder()
                .namePattern("*-chat*")
                .nodeName("worker-1")
                .mode(NotificationRuleEntity.Mode.EXCLUDE)
                .notifyIntentional(true)
                .enabled(false)
                .build();

        assertThat(entity.getNamePattern()).isEqualTo("*-chat*");
        assertThat(entity.getNodeName()).isEqualTo("worker-1");
        assertThat(entity.getMode()).isEqualTo(NotificationRuleEntity.Mode.EXCLUDE);
        assertThat(entity.isNotifyIntentional()).isTrue();
        assertThat(entity.isEnabled()).isFalse();
    }
}
