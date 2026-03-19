package com.lite_k8s.service;

import com.lite_k8s.config.SelfHealingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HealingRuleMatcherTest {

    private HealingRuleMatcher matcher;
    private SelfHealingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SelfHealingProperties();
        matcher = new HealingRuleMatcher(properties);
    }

    @Test
    void shouldReturnRuleWhenContainerNameExactlyMatches() {
        SelfHealingProperties.Rule rule = new SelfHealingProperties.Rule();
        rule.setNamePattern("web-server");
        rule.setMaxRestarts(3);
        properties.setRules(List.of(rule));

        Optional<SelfHealingProperties.Rule> result = matcher.findMatchingRule("web-server");

        assertThat(result).isPresent();
        assertThat(result.get().getNamePattern()).isEqualTo("web-server");
    }

    @Test
    void shouldReturnRuleWhenWildcardPatternMatches() {
        SelfHealingProperties.Rule rule = new SelfHealingProperties.Rule();
        rule.setNamePattern("web-*");
        rule.setMaxRestarts(3);
        properties.setRules(List.of(rule));

        Optional<SelfHealingProperties.Rule> result = matcher.findMatchingRule("web-server");

        assertThat(result).isPresent();
        assertThat(result.get().getNamePattern()).isEqualTo("web-*");
    }

    @Test
    void shouldReturnEmptyWhenNoRuleMatches() {
        SelfHealingProperties.Rule rule = new SelfHealingProperties.Rule();
        rule.setNamePattern("web-*");
        properties.setRules(List.of(rule));

        Optional<SelfHealingProperties.Rule> result = matcher.findMatchingRule("db-server");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnFirstMatchingRule() {
        SelfHealingProperties.Rule rule1 = new SelfHealingProperties.Rule();
        rule1.setNamePattern("web-*");
        rule1.setMaxRestarts(3);

        SelfHealingProperties.Rule rule2 = new SelfHealingProperties.Rule();
        rule2.setNamePattern("*-server");
        rule2.setMaxRestarts(5);

        properties.setRules(List.of(rule1, rule2));

        Optional<SelfHealingProperties.Rule> result = matcher.findMatchingRule("web-server");

        assertThat(result).isPresent();
        assertThat(result.get().getMaxRestarts()).isEqualTo(3);
    }
}
