package com.lite_k8s.service;

import com.lite_k8s.config.SelfHealingProperties;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class HealingRuleMatcherTest {

    @Mock private NodeRegistry nodeRegistry;
    @Mock private SelfHealingRuleService ruleService;

    private HealingRuleMatcher matcher;
    private SelfHealingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SelfHealingProperties();
        matcher = new HealingRuleMatcher(properties, nodeRegistry, ruleService);
        lenient().when(ruleService.findAllActive()).thenReturn(List.of());
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
    void shouldMatchRule_WhenNodeNameMatches() {
        // given: rule에 nodeName="res" 설정, 컨테이너도 res 노드에 있음
        Node resNode = Node.builder().id("uuid-res").name("res").host("h").port(2375).build();
        when(nodeRegistry.findByName("res")).thenReturn(Optional.of(resNode));

        SelfHealingProperties.Rule rule = new SelfHealingProperties.Rule();
        rule.setNamePattern("engine");
        rule.setNodeName("res");
        properties.setRules(List.of(rule));

        Optional<SelfHealingProperties.Rule> result = matcher.findMatchingRule("engine", "uuid-res");

        assertThat(result).isPresent();
    }

    @Test
    void shouldNotMatchRule_WhenNodeNameDiffers() {
        // given: rule에 nodeName="res", 컨테이너는 다른 노드
        Node resNode = Node.builder().id("uuid-res").name("res").host("h").port(2375).build();
        when(nodeRegistry.findByName("res")).thenReturn(Optional.of(resNode));

        SelfHealingProperties.Rule rule = new SelfHealingProperties.Rule();
        rule.setNamePattern("engine");
        rule.setNodeName("res");
        properties.setRules(List.of(rule));

        Optional<SelfHealingProperties.Rule> result = matcher.findMatchingRule("engine", "uuid-other");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldMatchRule_WhenNodeNameIsNull_AllNodes() {
        // given: rule에 nodeName 없음 → 모든 노드에 적용
        SelfHealingProperties.Rule rule = new SelfHealingProperties.Rule();
        rule.setNamePattern("engine");
        // nodeName 미설정
        properties.setRules(List.of(rule));

        Optional<SelfHealingProperties.Rule> result = matcher.findMatchingRule("engine", "any-node-id");

        assertThat(result).isPresent();
        verifyNoInteractions(nodeRegistry);
    }

    @Test
    void shouldMatchDbRule_WhenYamlRulesEmpty() {
        SelfHealingProperties.Rule dbRule = new SelfHealingProperties.Rule();
        dbRule.setNamePattern("api-*");
        dbRule.setMaxRestarts(7);
        when(ruleService.findAllActive()).thenReturn(List.of(dbRule));

        Optional<SelfHealingProperties.Rule> result = matcher.findMatchingRule("api-gateway");

        assertThat(result).isPresent();
        assertThat(result.get().getMaxRestarts()).isEqualTo(7);
    }

    @Test
    void shouldPreferDbRule_WhenNamePatternConflictsWithYaml() {
        // YAML rule
        SelfHealingProperties.Rule yamlRule = new SelfHealingProperties.Rule();
        yamlRule.setNamePattern("engine*");
        yamlRule.setMaxRestarts(3);
        properties.setRules(List.of(yamlRule));

        // DB rule with same pattern but different maxRestarts → DB wins
        SelfHealingProperties.Rule dbRule = new SelfHealingProperties.Rule();
        dbRule.setNamePattern("engine*");
        dbRule.setMaxRestarts(10);
        when(ruleService.findAllActive()).thenReturn(List.of(dbRule));

        Optional<SelfHealingProperties.Rule> result = matcher.findMatchingRule("engine-worker");

        assertThat(result).isPresent();
        assertThat(result.get().getMaxRestarts()).isEqualTo(10);
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
