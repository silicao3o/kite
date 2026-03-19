package com.lite_k8s.health;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Health Check Probe 설정
 *
 * 예시:
 * docker.monitor.health-check:
 *   enabled: true
 *   probes:
 *     - container-pattern: "demo-api-.*"
 *       liveness:
 *         type: HTTP
 *         path: /actuator/health
 *         port: 8081
 *         initial-delay-seconds: 30
 *         period-seconds: 15
 *         failure-threshold: 3
 *       readiness:
 *         type: HTTP
 *         path: /actuator/health/readiness
 *         port: 8081
 *         initial-delay-seconds: 10
 *         period-seconds: 10
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docker.monitor.health-check")
public class HealthCheckProperties {

    private boolean enabled = false;

    private List<ContainerProbeConfig> probes = new ArrayList<>();

    @Getter
    @Setter
    public static class ContainerProbeConfig {
        /** 대상 컨테이너 이름 패턴 (정규식) */
        private String containerPattern;

        /** Liveness Probe: 컨테이너 생존 확인 (실패 시 재시작) */
        private ProbeConfig liveness;

        /** Readiness Probe: 트래픽 수신 준비 확인 (실패 시 로그만, 재시작 안 함) */
        private ProbeConfig readiness;
    }
}
