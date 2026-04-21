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

        // 1. 네트워크 생성 (필요시)
        for (String network : svc.getNetworks()) {
            ensureNetwork(client, network);
        }

        // 2. env 구성: compose env + profile env merge (profile이 오버라이드)
        List<String> envList = buildEnvList(svc.getEnvironment(), envProfileId);

        // 3. 라벨 구성
        Map<String, String> labels = new LinkedHashMap<>(svc.getLabels() != null ? svc.getLabels() : Map.of());
        if (definitionId != null) {
            labels.put("kite.service-definition-id", definitionId);
        }
        if (envProfileId != null) {
            labels.put(EnvProfileResolver.LABEL_KEY, envProfileId);
        }

        // 4. HostConfig 구성 (ports, volumes, restart)
        HostConfig hostConfig = buildHostConfig(svc);

        // 5. 컨테이너 생성
        CreateContainerCmd cmd = client.createContainerCmd(svc.getImage())
                .withName(svc.getContainerName())
                .withHostConfig(hostConfig)
                .withEnv(envList)
                .withLabels(labels);

        CreateContainerResponse response = cmd.exec();
        String containerId = response.getId();

        // 6. 네트워크 연결
        for (String network : svc.getNetworks()) {
            try {
                client.connectToNetworkCmd()
                        .withNetworkId(network)
                        .withContainerId(containerId)
                        .exec();
            } catch (Exception e) {
                log.warn("네트워크 연결 실패: {} → {}", containerId, network, e);
            }
        }

        // 7. 시작
        client.startContainerCmd(containerId).exec();
        log.info("서비스 배포 완료: {} ({})", svc.getContainerName(), containerId);

        return containerId;
    }

    private List<String> buildEnvList(Map<String, String> composeEnv, String envProfileId) {
        Map<String, String> merged = new LinkedHashMap<>();

        // compose env 먼저
        if (composeEnv != null) {
            merged.putAll(composeEnv);
        }

        // profile env가 오버라이드
        if (envProfileId != null) {
            merged.putAll(envProfileResolver.resolve(List.of(envProfileId)));
        }

        // 변수 치환은 resolve() 내부에서 이미 처리됨
        // compose env의 ${KEY}도 치환
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            resolved.put(entry.getKey(), substituteVars(entry.getValue(), merged));
        }

        return resolved.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .toList();
    }

    private String substituteVars(String value, Map<String, String> context) {
        if (value == null || !value.contains("${")) return value;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\$\\{([^}]+)}").matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String varName = m.group(1);
            String replacement = context.get(varName);
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

    private void ensureNetwork(DockerClient client, String networkName) {
        try {
            List<com.github.dockerjava.api.model.Network> existing =
                    client.listNetworksCmd().withNameFilter(networkName).exec();
            if (existing.isEmpty()) {
                client.createNetworkCmd().withName(networkName).exec();
                log.info("네트워크 생성: {}", networkName);
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
