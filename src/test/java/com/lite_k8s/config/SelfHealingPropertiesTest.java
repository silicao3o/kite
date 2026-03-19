package com.lite_k8s.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SelfHealingPropertiesTest {

    @Test
    void shouldHaveEnabledPropertyDefaultToFalse() {
        SelfHealingProperties properties = new SelfHealingProperties();

        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void shouldHaveEmptyRulesListByDefault() {
        SelfHealingProperties properties = new SelfHealingProperties();

        assertThat(properties.getRules()).isNotNull().isEmpty();
    }

    @Test
    void shouldAllowAddingRules() {
        SelfHealingProperties properties = new SelfHealingProperties();
        SelfHealingProperties.Rule rule = new SelfHealingProperties.Rule();
        rule.setNamePattern("web-*");
        rule.setMaxRestarts(3);
        rule.setRestartDelaySeconds(10);

        properties.setRules(List.of(rule));

        assertThat(properties.getRules()).hasSize(1);
        assertThat(properties.getRules().get(0).getNamePattern()).isEqualTo("web-*");
        assertThat(properties.getRules().get(0).getMaxRestarts()).isEqualTo(3);
        assertThat(properties.getRules().get(0).getRestartDelaySeconds()).isEqualTo(10);
    }
}
