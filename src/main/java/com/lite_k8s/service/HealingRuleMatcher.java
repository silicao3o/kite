package com.lite_k8s.service;

import com.lite_k8s.config.SelfHealingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HealingRuleMatcher {

    private final SelfHealingProperties properties;

    public Optional<SelfHealingProperties.Rule> findMatchingRule(String containerName) {
        return properties.getRules().stream()
                .filter(rule -> matchesPattern(containerName, rule.getNamePattern()))
                .findFirst();
    }

    private boolean matchesPattern(String containerName, String pattern) {
        if (pattern == null || containerName == null) {
            return false;
        }
        // 와일드카드 패턴을 정규식으로 변환
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*");
        return containerName.matches(regex);
    }
}
