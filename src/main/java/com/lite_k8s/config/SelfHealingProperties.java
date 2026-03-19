package com.lite_k8s.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docker.monitor.self-healing")
public class SelfHealingProperties {

    private boolean enabled = false;
    private int resetWindowMinutes = 30;
    private List<Rule> rules = new ArrayList<>();

    @Getter
    @Setter
    public static class Rule {
        private String namePattern;
        private int maxRestarts = 3;
        private int restartDelaySeconds = 0; // 기본값 0 (즉시 재시작)
    }
}
