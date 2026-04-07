package com.lite_k8s.desired;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Desired State 기반 컨테이너 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContainerFactory {

    private final DockerClient dockerClient;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;

    /**
     * ServiceSpec 설정으로 새 컨테이너 생성 및 시작
     *
     * @param spec  서비스 스펙
     * @param index 컨테이너 인덱스 (1부터 시작, 이름 suffix로 사용)
     * @return 생성된 컨테이너 ID, 실패 시 null
     */
    public String create(DesiredStateProperties.ServiceSpec spec, int index) {
        String containerName = (index == 0)
                ? spec.getContainerNamePrefix()
                : spec.getContainerNamePrefix() + "-" + index;
        DockerClient client = resolveClient(spec);
        try {
            HostConfig hostConfig = buildHostConfig(spec);

            CreateContainerResponse response = client.createContainerCmd(spec.getImage())
                    .withName(containerName)
                    .withEnv(spec.getEnv().toArray(new String[0]))
                    .withHostConfig(hostConfig)
                    .withLabels(spec.getLabels())
                    .exec();

            client.startContainerCmd(response.getId()).exec();

            log.info("컨테이너 생성 완료: {} ({})", containerName, response.getId().substring(0, 12));
            return response.getId();

        } catch (Exception e) {
            log.error("컨테이너 생성 실패: {}", containerName, e);
            return null;
        }
    }

    private DockerClient resolveClient(DesiredStateProperties.ServiceSpec spec) {
        if (spec.getNodeName() != null) {
            return nodeRegistry.findByName(spec.getNodeName())
                    .map(nodeClientFactory::createClient)
                    .orElse(dockerClient);
        }
        if (spec.getNodeId() != null) {
            return nodeRegistry.findById(spec.getNodeId())
                    .map(nodeClientFactory::createClient)
                    .orElse(dockerClient);
        }
        return dockerClient;
    }

    private HostConfig buildHostConfig(DesiredStateProperties.ServiceSpec spec) {
        HostConfig hostConfig = HostConfig.newHostConfig();

        if (!spec.getPorts().isEmpty()) {
            List<PortBinding> bindings = new ArrayList<>();
            List<ExposedPort> exposedPorts = new ArrayList<>();

            for (String portMapping : spec.getPorts()) {
                // "8081:8081" → host:container
                String[] parts = portMapping.split(":");
                if (parts.length == 2) {
                    int hostPort = Integer.parseInt(parts[0].trim());
                    int containerPort = Integer.parseInt(parts[1].trim());
                    ExposedPort exposed = ExposedPort.tcp(containerPort);
                    exposedPorts.add(exposed);
                    bindings.add(PortBinding.parse(hostPort + ":" + containerPort));
                }
            }

            Ports portBindings = new Ports();
            for (PortBinding binding : bindings) {
                portBindings.add(binding);
            }
            hostConfig.withPortBindings(portBindings);
        }

        return hostConfig;
    }
}
