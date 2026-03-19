package com.lite_k8s.metrics;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "docker.monitor.metrics.retention")
public class MetricsRetentionProperties {
    private int retentionDays = 30;
}
