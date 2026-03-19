package com.lite_k8s.health;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import com.lite_k8s.service.DockerService;
import com.lite_k8s.util.DockerContainerNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Health Check 스케줄러
 *
 * 설정된 모든 컨테이너에 대해 주기적으로 probe를 실행하고,
 * liveness probe 연속 실패 시 컨테이너를 재시작
 */
@Slf4j
@Component
public class HealthCheckScheduler {

    private final HealthCheckProperties properties;
    private final ProbeRunner probeRunner;
    private final DockerClient dockerClient;
    private final DockerService dockerService;
    private final HealthCheckStateTracker stateTracker;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;

    @Autowired
    public HealthCheckScheduler(
            HealthCheckProperties properties,
            ProbeRunner probeRunner,
            DockerClient dockerClient,
            DockerService dockerService,
            HealthCheckStateTracker stateTracker,
            NodeRegistry nodeRegistry,
            NodeDockerClientFactory nodeClientFactory) {
        this.properties = properties;
        this.probeRunner = probeRunner;
        this.dockerClient = dockerClient;
        this.dockerService = dockerService;
        this.stateTracker = stateTracker;
        this.nodeRegistry = nodeRegistry;
        this.nodeClientFactory = nodeClientFactory;
    }

    // 기존 테스트 호환 생성자
    HealthCheckScheduler(
            HealthCheckProperties properties,
            ProbeRunner probeRunner,
            DockerClient dockerClient,
            DockerService dockerService,
            HealthCheckStateTracker stateTracker) {
        this(properties, probeRunner, dockerClient, dockerService, stateTracker, null, null);
    }

    @Scheduled(fixedDelayString = "${docker.monitor.health-check.period-seconds:15}000",
               initialDelayString = "${docker.monitor.health-check.initial-delay-seconds:30}000")
    public void runProbes() {
        if (!properties.isEnabled()) {
            return;
        }

        List<Node> nodes = nodeRegistry != null ? nodeRegistry.findAll() : List.of();

        if (!nodes.isEmpty()) {
            // 멀티 노드 모드: 노드별 컨테이너 조회
            for (Node node : nodes) {
                try {
                    DockerClient client = nodeClientFactory.createClient(node);
                    List<Container> running = client.listContainersCmd().withShowAll(false).exec();
                    runProbesOnContainers(running, client);
                } catch (Exception e) {
                    log.warn("[HealthCheck] 노드 {} 연결 실패, 스킵: {}", node.getName(), e.getMessage());
                }
            }
        } else {
            // 로컬 단일 모드 (기존 동작)
            List<Container> running = dockerClient.listContainersCmd().withShowAll(false).exec();
            runProbesOnContainers(running, dockerClient);
        }
    }

    private void runProbesOnContainers(List<Container> running, DockerClient client) {
        for (HealthCheckProperties.ContainerProbeConfig probeConfig : properties.getProbes()) {
            for (Container container : running) {
                String name = extractName(container);
                if (!matchesPattern(name, probeConfig.getContainerPattern())) {
                    continue;
                }

                String ip = getContainerIp(container.getId(), client);

                if (probeConfig.getLiveness() != null) {
                    runLivenessProbe(container, name, ip, probeConfig.getLiveness(), client);
                }
                if (probeConfig.getReadiness() != null) {
                    runReadinessProbe(container, name, ip, probeConfig.getReadiness());
                }
            }
        }
    }

    private void runLivenessProbe(Container container, String name, String ip, ProbeConfig probe, DockerClient client) {
        if (!stateTracker.isInitialDelayElapsed(container.getId(), probe.getInitialDelaySeconds())) {
            log.debug("initialDelay 대기 중: {}", name);
            return;
        }

        ProbeResult result = probeRunner.run(ip, container.getId(), probe);

        if (result.isSuccess()) {
            stateTracker.recordSuccess(container.getId(), "liveness");
            log.debug("[Liveness] {} → OK ({}ms)", name, result.getResponseTimeMs());
        } else {
            int failures = stateTracker.recordFailure(container.getId(), "liveness");
            log.warn("[Liveness] {} → FAIL ({}/{}) : {}",
                    name, failures, probe.getFailureThreshold(), result.getMessage());

            if (failures >= probe.getFailureThreshold()) {
                log.error("[Liveness] {} → 재시작 트리거 (연속 {}회 실패)", name, failures);
                boolean ok = dockerService.restartContainer(container.getId(), client);
                if (ok) {
                    stateTracker.reset(container.getId(), "liveness");
                    stateTracker.recordContainerStart(container.getId());
                }
            }
        }
    }

    private void runReadinessProbe(Container container, String name, String ip, ProbeConfig probe) {
        if (!stateTracker.isInitialDelayElapsed(container.getId(), probe.getInitialDelaySeconds())) {
            return;
        }

        ProbeResult result = probeRunner.run(ip, container.getId(), probe);

        if (result.isSuccess()) {
            stateTracker.recordSuccess(container.getId(), "readiness");
            log.debug("[Readiness] {} → Ready ({}ms)", name, result.getResponseTimeMs());
        } else {
            int failures = stateTracker.recordFailure(container.getId(), "readiness");
            log.warn("[Readiness] {} → NotReady ({}/{}) : {}",
                    name, failures, probe.getFailureThreshold(), result.getMessage());
            // Readiness 실패는 재시작 안 함 - 로그만 기록
        }
    }

    private String getContainerIp(String containerId, DockerClient client) {
        try {
            String ip = client.inspectContainerCmd(containerId)
                    .exec()
                    .getNetworkSettings()
                    .getIpAddress();
            if (ip != null && !ip.isBlank()) {
                return ip;
            }
            // Docker Compose 네트워크에서는 IpAddress가 빈 문자열일 수 있음
            // 컨테이너 이름으로 폴백
            String name = client.inspectContainerCmd(containerId)
                    .exec()
                    .getName();
            return DockerContainerNames.stripLeadingSlash(name != null ? name : "127.0.0.1");
        } catch (Exception e) {
            log.warn("컨테이너 IP 조회 실패: {}", containerId);
            return "127.0.0.1";
        }
    }

    private String extractName(Container container) {
        return DockerContainerNames.extractName(container);
    }

    private boolean matchesPattern(String name, String pattern) {
        if (pattern == null || pattern.isEmpty()) return true;
        return name.matches(pattern);
    }
}
