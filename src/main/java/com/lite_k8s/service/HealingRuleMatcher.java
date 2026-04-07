package com.lite_k8s.service;

import com.lite_k8s.config.SelfHealingProperties;
import com.lite_k8s.node.NodeRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HealingRuleMatcher {

    private final SelfHealingProperties properties;
    private final NodeRegistry nodeRegistry;

    /** 이전 호환: nodeId 없이 이름만으로 매칭 (모든 노드에 적용되는 규칙만) */
    public Optional<SelfHealingProperties.Rule> findMatchingRule(String containerName) {
        return findMatchingRule(containerName, null);
    }

    /** nodeId 포함 매칭: rule.nodeName이 있으면 해당 노드만, 없으면 모든 노드 */
    public Optional<SelfHealingProperties.Rule> findMatchingRule(String containerName, String containerNodeId) {
        return properties.getRules().stream()
                .filter(rule -> matchesPattern(containerName, rule.getNamePattern()))
                .filter(rule -> matchesNode(rule, containerNodeId))
                .findFirst();
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
