package com.lite_k8s.service;

import com.lite_k8s.config.SelfHealingProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class ContainerLabelReader {

    private static final String LABEL_PREFIX = "self-healing.";
    private static final String ENABLED = LABEL_PREFIX + "enabled";
    private static final String MAX_RESTARTS = LABEL_PREFIX + "max-restarts";

    public Optional<SelfHealingProperties.Rule> readHealingConfig(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return Optional.empty();
        }

        String enabled = labels.get(ENABLED);
        if (enabled == null || !Boolean.parseBoolean(enabled)) {
            return Optional.empty();
        }

        SelfHealingProperties.Rule rule = new SelfHealingProperties.Rule();
        rule.setNamePattern("label-based");

        String maxRestarts = labels.get(MAX_RESTARTS);
        if (maxRestarts != null) {
            try {
                rule.setMaxRestarts(Integer.parseInt(maxRestarts));
            } catch (NumberFormatException e) {
                rule.setMaxRestarts(3);
            }
        }

        return Optional.of(rule);
    }
}
