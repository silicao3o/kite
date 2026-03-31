package com.lite_k8s.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import com.lite_k8s.util.DockerContainerNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContainerRecreateService {

    private final DockerClient dockerClient;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;

    public String getImageName(String containerId) {
        return dockerClient.inspectContainerCmd(containerId).exec()
                .getConfig().getImage();
    }

    public void pullImage(DockerClient client, String imageName) {
        try {
            client.pullImageCmd(imageName)
                    .exec(new ResultCallback.Adapter<PullResponseItem>())
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

        pullImage(client, config.imageName());

        client.stopContainerCmd(containerId).exec();
        client.removeContainerCmd(containerId).exec();

        CreateContainerResponse created = client.createContainerCmd(config.imageName())
                .withName(config.containerName())
                .withEnv(config.env())
                .withHostConfig(config.hostConfig())
                .withLabels(config.labels())
                .exec();

        client.startContainerCmd(created.getId()).exec();
        log.info("이미지 업데이트 완료: {} ({})", config.containerName(), config.imageName());
    }

    private DockerClient resolveClient(String nodeId) {
        if (nodeId == null || nodeRegistry == null) return dockerClient;
        return nodeRegistry.findById(nodeId)
                .map(nodeClientFactory::createClient)
                .orElse(dockerClient);
    }
}
