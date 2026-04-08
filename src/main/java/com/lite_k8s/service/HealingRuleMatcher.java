package com.lite_k8s.service;

import com.lite_k8s.config.SelfHealingProperties;
import com.lite_k8s.node.NodeRegistry;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Service
public class HealingRuleMatcher {

    private final SelfHealingProperties properties;
    private final NodeRegistry nodeRegistry;
    private final SelfHealingRuleService ruleService;

    public HealingRuleMatcher(SelfHealingProperties properties,
                              NodeRegistry nodeRegistry,
                              @Lazy SelfHealingRuleService ruleService) {
        this.properties = properties;
        this.nodeRegistry = nodeRegistry;
        this.ruleService = ruleService;
    }

    /** 이전 호환: nodeId 없이 이름만으로 매칭 (모든 노드에 적용되는 규칙만) */
    public Optional<SelfHealingProperties.Rule> findMatchingRule(String containerName) {
        return findMatchingRule(containerName, null);
    }

    /** nodeId 포함 매칭: rule.nodeName이 있으면 해당 노드만, 없으면 모든 노드 */
    public Optional<SelfHealingProperties.Rule> findMatchingRule(String containerName, String containerNodeId) {
        return mergeRules().stream()
                .filter(rule -> matchesPattern(containerName, rule.getNamePattern()))
                .filter(rule -> matchesNode(rule, containerNodeId))
                .findFirst();
    }

    /**
     * YAML rules + DB rules 합산. namePattern 충돌 시 DB 규칙이 YAML 규칙을 덮어쓴다.
     * 병합 순서: DB 규칙 먼저, 그 다음 YAML 중 중복되지 않는 것.
     */
    private List<SelfHealingProperties.Rule> mergeRules() {
        LinkedHashMap<String, SelfHealingProperties.Rule> merged = new LinkedHashMap<>();

        List<SelfHealingProperties.Rule> dbRules = ruleService == null ? List.of() : ruleService.findAllActive();
        for (SelfHealingProperties.Rule rule : dbRules) {
            merged.put(rule.getNamePattern(), rule);
        }
        for (SelfHealingProperties.Rule rule : properties.getRules()) {
            merged.putIfAbsent(rule.getNamePattern(), rule);
        }
        return new ArrayList<>(merged.values());
    }

    private boolean matchesNode(SelfHealingProperties.Rule rule, String containerNodeId) {
        if (rule.getNodeName() == null) return true; // 모든 노드에 적용
        if (containerNodeId == null) return false;
        return nodeRegistry.findByName(rule.getNodeName())
                .map(node -> node.getId().equals(containerNodeId))
                .orElse(false);
    }

    private boolean matchesPattern(String containerName, String pattern) {
        if (pattern == null || containerName == null) {
            return false;
        }
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*");
        return containerName.matches(regex);
    }
}
