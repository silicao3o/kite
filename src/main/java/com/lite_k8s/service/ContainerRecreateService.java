package com.lite_k8s.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.lite_k8s.compose.ComposeParser;
import com.lite_k8s.compose.ParsedService;
import com.lite_k8s.compose.ServiceDefinition;
import com.lite_k8s.compose.ServiceDefinitionRepository;
import com.lite_k8s.compose.ServiceDeployer;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import com.lite_k8s.util.DockerContainerNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContainerRecreateService {

    private final DockerClient dockerClient;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;
    private final OwnActionTracker ownActionTracker;
    private final ServiceDefinitionRepository serviceDefinitionRepository;
    private final ServiceDeployer serviceDeployer;

    @Value("${GHCR_TOKEN:}")
    private String ghcrToken;

    public String getImageName(String containerId) {
        return dockerClient.inspectContainerCmd(containerId).exec()
                .getConfig().getImage();
    }

    public void pullImage(DockerClient client, String imageName) {
        try {
            var cmd = client.pullImageCmd(imageName)
                    .withPlatform("linux/amd64");
            if (ghcrToken != null && !ghcrToken.isBlank() && imageName.startsWith("ghcr.io")) {
                String[] parts = imageName.split("/");
                String username = parts.length >= 2 ? parts[1] : "token";
                cmd.withAuthConfig(new AuthConfig()
                        .withRegistryAddress("https://ghcr.io")
                        .withUsername(username)
                        .withPassword(ghcrToken));
            }
            cmd.exec(new ResultCallback.Adapter<PullResponseItem>())
                    .awaitCompletion(120, TimeUnit.SECONDS);
            log.info("이미지 Pull 완료: {}", imageName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("이미지 Pull 중단: " + imageName, e);
        } catch (Exception e) {
            throw new RuntimeException("이미지 Pull 실패: " + imageName, e);
        }
    }

    public ContainerRecreateConfig buildConfig(InspectContainerResponse inspect) {
        String name = DockerContainerNames.stripLeadingSlash(inspect.getName());
        String image = inspect.getConfig().getImage();
        String[] env = inspect.getConfig().getEnv() != null ? inspect.getConfig().getEnv() : new String[]{};
        Map<String, String> labels = inspect.getConfig().getLabels() != null ? inspect.getConfig().getLabels() : Map.of();
        HostConfig hostConfig = inspect.getHostConfig() != null ? inspect.getHostConfig() : HostConfig.newHostConfig();
        return new ContainerRecreateConfig(image, name, env, hostConfig, labels);
    }

    public void pullAndRecreate(String containerId, String nodeId) {
        DockerClient client = resolveClient(nodeId);

        InspectContainerResponse inspect = client.inspectContainerCmd(containerId).exec();
        ContainerRecreateConfig config = buildConfig(inspect);

        // 서비스 정의 라벨이 있으면 DB 기반 재배포 (최신 compose + env profile 적용)
        String definitionId = config.labels().get("kite.service-definition-id");
        if (definitionId != null && serviceDefinitionRepository != null) {
            var maybeDef = serviceDefinitionRepository.findById(definitionId);
            if (maybeDef.isPresent()) {
                pullAndRecreateFromDefinition(client, containerId, config, maybeDef.get(), nodeId);
                return;
            }
        }

        // 서비스 정의가 없으면 기존 방식 (컨테이너 설정 복사)
        pullImage(client, config.imageName());

        ownActionTracker.markOwnAction(containerId);
        client.stopContainerCmd(containerId).exec();
        client.removeContainerCmd(containerId).exec();

        CreateContainerResponse created = client.createContainerCmd(config.imageName())
                .withName(config.containerName())
                .withEnv(config.env())
                .withHostConfig(config.hostConfig())
                .withLabels(config.labels())
                .exec();

        client.startContainerCmd(created.getId()).exec();
        log.info("이미지 업데이트 완료 (기존 설정): {} ({})", config.containerName(), config.imageName());
    }

    /**
     * DB 서비스 정의 기반 재배포 — 최신 compose YAML + env profile로 재생성
     */
    private void pullAndRecreateFromDefinition(DockerClient client, String containerId,
                                                ContainerRecreateConfig config,
                                                ServiceDefinition def, String nodeId) {
        String containerName = config.containerName();
        List<ParsedService> services = ComposeParser.parse(def.getComposeYaml());

        // 컨테이너 이름으로 매칭되는 서비스 찾기
        ParsedService matchedSvc = services.stream()
                .filter(s -> {
                    String svcName = s.getContainerName() != null ? s.getContainerName() : s.getServiceName();
                    return containerName.contains(svcName);
                })
                .findFirst()
                .orElse(null);

        if (matchedSvc == null) {
            log.warn("서비스 정의에서 매칭 서비스를 찾을 수 없어 기존 설정으로 재생성: {}", containerName);
            pullImage(client, config.imageName());
            recreateFromConfig(client, containerId, config);
            return;
        }

        // 노드 이름으로 env profile ID 조회
        String nodeName = resolveNodeName(nodeId);
        Map<String, String> mappings = def.getNodeEnvMappings();
        String profileId = mappings != null ? mappings.get(nodeName) : null;

        // stop + remove
        ownActionTracker.markOwnAction(containerId);
        client.stopContainerCmd(containerId).exec();
        client.removeContainerCmd(containerId).exec();

        // ServiceDeployer로 최신 설정 배포
        String newId = serviceDeployer.deployWithDefinitionId(matchedSvc, profileId, nodeId, def.getId());
        log.info("이미지 업데이트 완료 (DB 설정): {} → {} (def={})", containerName, newId, def.getName());
    }

    private void recreateFromConfig(DockerClient client, String containerId, ContainerRecreateConfig config) {
        ownActionTracker.markOwnAction(containerId);
        client.stopContainerCmd(containerId).exec();
        client.removeContainerCmd(containerId).exec();

        CreateContainerResponse created = client.createContainerCmd(config.imageName())
                .withName(config.containerName())
                .withEnv(config.env())
                .withHostConfig(config.hostConfig())
                .withLabels(config.labels())
                .exec();
        client.startContainerCmd(created.getId()).exec();
    }

    private String resolveNodeName(String nodeId) {
        if (nodeId == null) return "";
        if (nodeRegistry == null) return nodeId;
        return nodeRegistry.findById(nodeId)
                .map(Node::getName)
                .orElse(nodeId);
    }

    private DockerClient resolveClient(String nodeId) {
        if (nodeId == null || nodeRegistry == null) return dockerClient;
        return nodeRegistry.findById(nodeId)
                .map(nodeClientFactory::createClient)
                .orElse(dockerClient);
    }
}
