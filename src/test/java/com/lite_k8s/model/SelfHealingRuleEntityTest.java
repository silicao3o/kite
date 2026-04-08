package com.lite_k8s.model;

import com.lite_k8s.config.SelfHealingProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SelfHealingRuleEntityTest {

    @Test
    void shouldCreateEntityWithDefaultIdAndCreatedAt() {
        SelfHealingRuleEntity entity = SelfHealingRuleEntity.builder()
                .namePattern("engine*")
                .maxRestarts(5)
                .restartDelaySeconds(10)
                .nodeName("res")
                .enabled(true)
                .build();

        assertThat(entity.getId()).isNotBlank();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getNamePattern()).isEqualTo("engine*");
        assertThat(entity.getMaxRestarts()).isEqualTo(5);
        assertThat(entity.getRestartDelaySeconds()).isEqualTo(10);
        assertThat(entity.getNodeName()).isEqualTo("res");
        assertThat(entity.isEnabled()).isTrue();
    }

    @Test
    void shouldConvertToSelfHealingPropertiesRule() {
        SelfHealingRuleEntity entity = SelfHealingRuleEntity.builder()
                .namePattern("*chat*")
                .maxRestarts(3)
                .restartDelaySeconds(2)
                .nodeName("worker-1")
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();

        SelfHealingProperties.Rule rule = entity.toRule();

        assertThat(rule.getNamePattern()).isEqualTo("*chat*");
        assertThat(rule.getMaxRestarts()).isEqualTo(3);
        assertThat(rule.getRestartDelaySeconds()).isEqualTo(2);
        assertThat(rule.getNodeName()).isEqualTo("worker-1");
    }
}
