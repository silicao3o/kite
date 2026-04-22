package com.lite_k8s.compose;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.model.*;
import com.lite_k8s.envprofile.EnvProfileResolver;
import com.lite_k8s.envprofile.ImageRegistryRepository;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceDeployer {

    private final DockerClient dockerClient;
    private final EnvProfileResolver envProfileResolver;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;
    private final ImageRegistryRepository imageRegistryRepository;

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

        // 3. 네트��크 생성/매칭 (실제 이름으로 resolve)
        List<String> resolvedNetworks = new ArrayList<>();
        if (resolved.getNetworks() != null) {
            for (String network : resolved.getNetworks()) {
                resolvedNetworks.add(ensureNetwork(client, network));
            }
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
        if (!resolvedNetworks.isEmpty()) {
            hostConfig.withNetworkMode(resolvedNetworks.get(0));
        }

        // 6. 같은 이름의 기존 컨테이너가 있으면 stop + remove
        removeExistingContainer(client, resolved.getContainerName());

        // 7. 이미지 pull (없으면 자동 다운로드)
        pullImageIfNeeded(client, resolved.getImage());

        // 8. 컨테이너 생성
        CreateContainerCmd cmd = client.createContainerCmd(resolved.getImage())
                .withName(resolved.getContainerName())
                .withHostConfig(hostConfig)
                .withEnv(envList)
                .withLabels(labels);

        CreateContainerResponse response = cmd.exec();
        String containerId = response.getId();

        // 8. 추가 네트워크 연결 (2번째부터)
        for (int i = 1; i < resolvedNetworks.size(); i++) {
            try {
                client.connectToNetworkCmd()
                        .withNetworkId(resolvedNetworks.get(i))
                        .withContainerId(containerId)
                        .exec();
            } catch (Exception e) {
                log.warn("네트워크 연결 실패: {} → {}", containerId, resolvedNetworks.get(i), e);
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
                .ports(substituteList(svc.getPorts(), context))
                .volumes(substituteList(svc.getVolumes(), context))
                .environment(svc.getEnvironment())
                .networks(svc.getNetworks())
                .restartPolicy(svc.getRestartPolicy())
                .labels(svc.getLabels())
                .extraHosts(substituteList(svc.getExtraHosts(), context))
                .dependsOn(svc.getDependsOn())
                .memoryLimit(substituteVars(svc.getMemoryLimit(), context))
                .cpuLimit(substituteVars(svc.getCpuLimit(), context))
                .build();
    }

    private List<String> substituteList(List<String> list, Map<String, String> context) {
        if (list == null) return List.of();
        return list.stream().map(v -> substituteVars(v, context)).toList();
    }

    private String substituteVars(String value, Map<String, String> context) {
        if (value == null || !value.contains("${")) return value;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\$\\{([^}]+)}").matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String expr = m.group(1);
            String varName;
            String defaultValue = null;
            boolean required = false;
            String requiredMsg = null;

            // ${KEY:?error message} — 필수 변수 (없으면 에러)
            int reqIdx = expr.indexOf(":?");
            // ${KEY:-default} — 기본값
            int defIdx = expr.indexOf(":-");

            if (reqIdx >= 0) {
                varName = expr.substring(0, reqIdx);
                requiredMsg = expr.substring(reqIdx + 2);
                required = true;
            } else if (defIdx >= 0) {
                varName = expr.substring(0, defIdx);
                defaultValue = expr.substring(defIdx + 2);
            } else {
                varName = expr;
            }

            String replacement = context.get(varName);
            if (replacement == null && required) {
                throw new IllegalStateException("필수 변수 미설정: " + varName + " (" + requiredMsg + ")");
            }
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

        // Volumes (bind mounts + named volumes)
        if (svc.getVolumes() != null && !svc.getVolumes().isEmpty()) {
            List<Bind> binds = new ArrayList<>();
            for (String vol : svc.getVolumes()) {
                String[] parts = vol.split(":");
                if (parts.length >= 2) {
                    AccessMode mode = AccessMode.rw;
                    if (parts.length >= 3 && "ro".equals(parts[2])) {
                        mode = AccessMode.ro;
                    }
                    binds.add(new Bind(parts[0], new Volume(parts[1]), mode));
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

        // Extra hosts
        if (svc.getExtraHosts() != null && !svc.getExtraHosts().isEmpty()) {
            hostConfig.withExtraHosts(svc.getExtraHosts().toArray(new String[0]));
        }

        // Memory limit (e.g. "2G", "512M")
        if (svc.getMemoryLimit() != null) {
            hostConfig.withMemory(parseMemoryBytes(svc.getMemoryLimit()));
        }

        // CPU limit (e.g. "1", "0.5" → nanoCpus)
        if (svc.getCpuLimit() != null) {
            try {
                long nanoCpus = (long) (Double.parseDouble(svc.getCpuLimit()) * 1_000_000_000L);
                hostConfig.withNanoCPUs(nanoCpus);
            } catch (NumberFormatException ignored) {}
        }

        return hostConfig;
    }

    private long parseMemoryBytes(String mem) {
        mem = mem.trim().toUpperCase();
        try {
            if (mem.endsWith("G")) return (long) (Double.parseDouble(mem.replace("G", "")) * 1024 * 1024 * 1024);
            if (mem.endsWith("M")) return (long) (Double.parseDouble(mem.replace("M", "")) * 1024 * 1024);
            if (mem.endsWith("K")) return (long) (Double.parseDouble(mem.replace("K", "")) * 1024);
            return Long.parseLong(mem);
        } catch (NumberFormatException e) { return 0; }
    }

    private void pullImageIfNeeded(DockerClient client, String image) {
        try {
            client.inspectImageCmd(image).exec();
            log.debug("이미지 존재: {}", image);
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            log.info("이미지 pull 시작: {}", image);
            try {
                PullImageCmd cmd = client.pullImageCmd(image).withPlatform("linux/amd64");

                // GHCR private 이미지면 레지스트리 토큰으로 인증
                if (image.startsWith("ghcr.io")) {
                    String token = resolveGhcrToken(image);
                    if (token != null) {
                        String[] parts = image.split("/");
                        String username = parts.length >= 2 ? parts[1] : "token";
                        cmd.withAuthConfig(new AuthConfig()
                                .withRegistryAddress("https://ghcr.io")
                                .withUsername(username)
                                .withPassword(token));
                        log.debug("GHCR 인증 적용: {}", image);
                    }
                }

                cmd.exec(new ResultCallback.Adapter<PullResponseItem>() {})
                        .awaitCompletion(300, TimeUnit.SECONDS);
                log.info("이미지 pull 완료: {}", image);
            } catch (Exception pullEx) {
                log.warn("이미지 pull 실패 (계속 진행): {}", image, pullEx);
            }
        }
    }

    /** 이미지 경로에서 레지스트리 토큰 조회 (image:tag → image로 검색) */
    private String resolveGhcrToken(String imageWithTag) {
        String imageOnly = imageWithTag.contains(":") ? imageWithTag.substring(0, imageWithTag.lastIndexOf(":")) : imageWithTag;
        return imageRegistryRepository.findByImage(imageOnly)
                .map(r -> r.getGhcrToken())
                .filter(t -> t != null && !t.isBlank())
                .orElse(null);
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

    /**
     * 네트워크 확인/생성. 정확한 이름이 없으면 suffix 매칭으로 기존 네트워크 검색.
     * 예: "quvi-net" → "daquv_quvi-net" 매칭 (docker-compose prefix)
     * @return 실제 사용할 네트워크 이름
     */
    private String ensureNetwork(DockerClient client, String networkName) {
        try {
            List<com.github.dockerjava.api.model.Network> all =
                    client.listNetworksCmd().exec();

            // 1. 정확한 이름 매칭
            for (var n : all) {
                if (networkName.equals(n.getName())) {
                    log.debug("네트워크 정확 매칭: {}", networkName);
                    return networkName;
                }
            }

            // 2. suffix 매��� (docker-compose prefix 대응: xxx_quvi-net)
            for (var n : all) {
                if (n.getName() != null && n.getName().endsWith("_" + networkName)) {
                    log.info("네트워크 suffix 매칭: {} → {}", networkName, n.getName());
                    return n.getName();
                }
            }

            // 3. 없으면 새로 생성
            client.createNetworkCmd().withName(networkName).withDriver("bridge").exec();
            log.info("네트워크 생성 완료: {}", networkName);
            return networkName;
        } catch (Exception e) {
            log.warn("네트워크 확인/생성 실패: {}", networkName, e);
            return networkName;
        }
    }

    private DockerClient resolveClient(String nodeId) {
        if (nodeId == null) return dockerClient;
        return nodeRegistry.findById(nodeId)
                .map(nodeClientFactory::createClient)
                .orElse(dockerClient);
    }
}
