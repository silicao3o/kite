package com.lite_k8s.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.model.Container;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    private final DockerClient dockerClient;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;

    public void deleteImage(String imageName, String nodeId) {
        DockerClient client = resolveClient(nodeId);
        try {
            client.removeImageCmd(imageName).exec();
        } catch (ConflictException e) {
            removeStoppedContainersUsing(client, imageName);
            client.removeImageCmd(imageName).exec();
        }
        log.info("이미지 삭제 완료: {}", imageName);
    }

    private void removeStoppedContainersUsing(DockerClient client, String imageName) {
        List<Container> containers = client.listContainersCmd()
                .withShowAll(true)
                .withAncestorFilter(List.of(imageName))
                .exec();
        for (Container c : containers) {
            if (!"running".equals(c.getState())) {
                log.info("중지된 컨테이너 제거: {} ({})", c.getId(), imageName);
                client.removeContainerCmd(c.getId()).exec();
            }
        }
    }

    private DockerClient resolveClient(String nodeId) {
        if (nodeId == null) return dockerClient;
        return nodeRegistry.findById(nodeId)
                .map(nodeClientFactory::createClient)
                .orElse(dockerClient);
    }
}
