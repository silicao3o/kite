package com.lite_k8s.compose;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.lite_k8s.envprofile.EnvProfileResolver;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceDeployer {

    private final DockerClient dockerClient;
    private final EnvProfileResolver envProfileResolver;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;

    public String deploy(ParsedService svc, String envProfileId, String nodeId) {
        return deployWithDefinitionId(svc, envProfileId, nodeId, null);
    }

    public String deployWithDefinitionId(ParsedService svc, String envProfileId, String nodeId, String definitionId) {
        DockerClient client = resolveClient(nodeId);

        // 1. env 구성: compose env + profile env merge (profile이 오버라이드)
        Map<String, String> envContext = buildEnvContext(svc.getEnvironment(), envProfileId);
        List<String> envList = envContext.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).toList();

        // 2. 전체 필드에 ${KEY} 변수 치환 (image, containerName, ports, volumes 등)
        ParsedService resolved = substituteAllFields(svc, envContext);

        // 3. 네트워크 생성 (필요시)
        for (String network : resolved.getNetworks()) {
            ensureNetwork(client, network);
        }

        // 4. 라벨 구성
        Map<String, String> labels = new LinkedHashMap<>(resolved.getLabels() != null ? resolved.getLabels() : Map.of());
        if (definitionId != null) {
            labels.put("kite.service-definition-id", definitionId);
        }
        if (envProfileId != null) {
            labels.put(EnvProfileResolver.LABEL_KEY, envProfileId);
        }

        // 5. HostConfig 구성 (ports, volumes, restart, 첫 번째 네트워크)
        HostConfig hostConfig = buildHostConfig(resolved);
        if (resolved.getNetworks() != null && !resolved.getNetworks().isEmpty()) {
            hostConfig.withNetworkMode(resolved.getNetworks().get(0));
        }

        // 6. 같은 이름의 기존 컨테이너가 있으면 stop + remove
        removeExistingContainer(client, resolved.getContainerName());

        // 7. 컨테이너 생성
        CreateContainerCmd cmd = client.createContainerCmd(resolved.getImage())
                .withName(resolved.getContainerName())
                .withHostConfig(hostConfig)
                .withEnv(envList)
                .withLabels(labels);

        CreateContainerResponse response = cmd.exec();
        String containerId = response.getId();

        // 8. 추가 네트워크 연결 (2번째부터)
        if (resolved.getNetworks() != null && resolved.getNetworks().size() > 1) {
            for (int i = 1; i < resolved.getNetworks().size(); i++) {
                try {
                    client.connectToNetworkCmd()
                            .withNetworkId(resolved.getNetworks().get(i))
                            .withContainerId(containerId)
                            .exec();
                } catch (Exception e) {
                    log.warn("네트워크 연결 실패: {} → {}", containerId, resolved.getNetworks().get(i), e);
                }
            }
        }

        // 9. 시작
        client.startContainerCmd(containerId).exec();
        log.info("서비스 배포 완료: {} ({})", resolved.getContainerName(), containerId);

        return containerId;
    }

    private Map<String, String> buildEnvContext(Map<String, String> composeEnv, String envProfileId) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (composeEnv != null) merged.putAll(composeEnv);
        if (envProfileId != null) merged.putAll(envProfileResolver.resolve(List.of(envProfileId)));

        // 변수 치환
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            resolved.put(entry.getKey(), substituteVars(entry.getValue(), merged));
        }
        return resolved;
    }

    /** ParsedService의 모든 문자열 필드에 ${KEY} 변수 치환 적용 */
    private ParsedService substituteAllFields(ParsedService svc, Map<String, String> context) {
        return ParsedService.builder()
                .serviceName(svc.getServiceName())
                .image(substituteVars(svc.getImage(), context))
                .containerName(substituteVars(svc.getContainerName(), context))
                .ports(svc.getPorts() != null ? svc.getPorts().stream().map(p -> substituteVars(p, context)).toList() : List.of())
                .volumes(svc.getVolumes() != null ? svc.getVolumes().stream().map(v -> substituteVars(v, context)).toList() : List.of())
                .environment(svc.getEnvironment())
                .networks(svc.getNetworks())
                .restartPolicy(svc.getRestartPolicy())
                .labels(svc.getLabels())
                .build();
    }

    private String substituteVars(String value, Map<String, String> context) {
        if (value == null || !value.contains("${")) return value;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\$\\{([^}]+)}").matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String expr = m.group(1);
            String varName;
            String defaultValue = null;

            // ${KEY:-default} 구문 지원
            int colonIdx = expr.indexOf(":-");
            if (colonIdx >= 0) {
                varName = expr.substring(0, colonIdx);
                defaultValue = expr.substring(colonIdx + 2);
            } else {
                varName = expr;
            }

            String replacement = context.get(varName);
            if (replacement == null && defaultValue != null) {
                replacement = defaultValue;
            }
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement != null ? replacement : m.group(0)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private HostConfig buildHostConfig(ParsedService svc) {
        HostConfig hostConfig = HostConfig.newHostConfig();

        // Port bindings
        if (svc.getPorts() != null && !svc.getPorts().isEmpty()) {
            Ports ports = new Ports();
            List<ExposedPort> exposedPorts = new ArrayList<>();
            for (String portMapping : svc.getPorts()) {
                String[] parts = portMapping.split(":");
                if (parts.length == 2) {
                    int hostPort = Integer.parseInt(parts[0].trim());
                    int containerPort = Integer.parseInt(parts[1].trim());
                    ExposedPort ep = ExposedPort.tcp(containerPort);
                    exposedPorts.add(ep);
                    ports.bind(ep, Ports.Binding.bindPort(hostPort));
                }
            }
            hostConfig.withPortBindings(ports);
        }

        // Volumes (bind mounts)
        if (svc.getVolumes() != null && !svc.getVolumes().isEmpty()) {
            List<Bind> binds = new ArrayList<>();
            for (String vol : svc.getVolumes()) {
                String[] parts = vol.split(":");
                if (parts.length >= 2) {
                    binds.add(new Bind(parts[0], new Volume(parts[1])));
                }
            }
            hostConfig.withBinds(binds);
        }

        // Restart policy
        if (svc.getRestartPolicy() != null) {
            switch (svc.getRestartPolicy()) {
                case "always" -> hostConfig.withRestartPolicy(RestartPolicy.alwaysRestart());
                case "unless-stopped" -> hostConfig.withRestartPolicy(RestartPolicy.unlessStoppedRestart());
                case "on-failure" -> hostConfig.withRestartPolicy(RestartPolicy.onFailureRestart(3));
                default -> hostConfig.withRestartPolicy(RestartPolicy.noRestart());
            }
        }

        return hostConfig;
    }

    private void removeExistingContainer(DockerClient client, String containerName) {
        try {
            var containers = client.listContainersCmd()
                    .withShowAll(true)
                    .withNameFilter(List.of(containerName))
                    .exec();
            for (var container : containers) {
                String name = container.getNames() != null && container.getNames().length > 0
                        ? container.getNames()[0].replaceFirst("^/", "") : "";
                if (name.equals(containerName)) {
                    try { client.stopContainerCmd(container.getId()).exec(); } catch (Exception ignored) {}
                    client.removeContainerCmd(container.getId()).exec();
                    log.info("기존 컨테이너 제거: {} ({})", containerName, container.getId());
                }
            }
        } catch (Exception e) {
            log.debug("기존 컨테이너 확인 중 오류 (무시): {}", containerName, e);
        }
    }

    private void ensureNetwork(DockerClient client, String networkName) {
        try {
            List<com.github.dockerjava.api.model.Network> existing =
                    client.listNetworksCmd().withNameFilter(networkName).exec();
            // withNameFilter는 부분 일치할 수 있으므로 정확히 매칭 확인
            boolean exactMatch = existing.stream()
                    .anyMatch(n -> networkName.equals(n.getName()));
            if (!exactMatch) {
                client.createNetworkCmd().withName(networkName).withDriver("bridge").exec();
                log.info("네트워크 생성 완료: {}", networkName);
            } else {
                log.debug("네트워크 이미 존재: {}", networkName);
            }
        } catch (Exception e) {
            log.warn("네트워크 확인/생성 실패: {}", networkName, e);
        }
    }

    private DockerClient resolveClient(String nodeId) {
        if (nodeId == null) return dockerClient;
        return nodeRegistry.findById(nodeId)
                .map(nodeClientFactory::createClient)
                .orElse(dockerClient);
    }
}
