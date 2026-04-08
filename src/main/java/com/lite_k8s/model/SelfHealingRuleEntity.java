package com.lite_k8s.model;

import com.lite_k8s.config.SelfHealingProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "self_healing_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelfHealingRuleEntity {

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String namePattern;
    private int maxRestarts;
    private int restartDelaySeconds;
    private String nodeName;
    private boolean enabled;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public SelfHealingProperties.Rule toRule() {
        SelfHealingProperties.Rule rule = new SelfHealingProperties.Rule();
        rule.setNamePattern(namePattern);
        rule.setMaxRestarts(maxRestarts);
        rule.setRestartDelaySeconds(restartDelaySeconds);
        rule.setNodeName(nodeName);
        return rule;
    }
}
