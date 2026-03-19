package com.lite_k8s.deploy;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.HostConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * 배포 전략 공통 Docker 조작 유틸
 * 각 DeploymentStrategy가 사용
 */
@Slf4j
public class ContainerOperator {

    private final DockerClient dockerClient;

    public ContainerOperator(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    /**
     * 기존 컨테이너 설정을 유지하면서 새 이미지로 새 컨테이너 생성·시작
     * @return 새 컨테이너 ID, 실패 시 null
     */
    public String createAndStart(String sourceContainerId, String newImage, String newName) {
        try {
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(sourceContainerId).exec();
            HostConfig hostConfig = inspect.getHostConfig() != null
                    ? inspect.getHostConfig() : HostConfig.newHostConfig();
            String[] env = inspect.getConfig().getEnv() != null
                    ? inspect.getConfig().getEnv() : new String[]{};

            CreateContainerResponse created = dockerClient.createContainerCmd(newImage)
                    .withName(newName)
                    .withHostConfig(hostConfig)
                    .withEnv(env)
                    .withLabels(inspect.getConfig().getLabels() != null
                            ? inspect.getConfig().getLabels() : java.util.Map.of())
                    .exec();

            dockerClient.startContainerCmd(created.getId()).exec();
            log.info("컨테이너 생성: {} → {}", newName, created.getId().substring(0, 12));
            return created.getId();

        } catch (Exception e) {
            log.error("컨테이너 생성 실패: {} ({})", newName, newImage, e);
            return null;
        }
    }

    /** 컨테이너 중지 */
    public boolean stop(String containerId) {
        try {
            dockerClient.stopContainerCmd(containerId).exec();
            return true;
        } catch (Exception e) {
            log.error("컨테이너 중지 실패: {}", containerId, e);
            return false;
        }
    }

    /** 컨테이너 제거 */
    public boolean remove(String containerId) {
        try {
            dockerClient.removeContainerCmd(containerId).exec();
            return true;
        } catch (Exception e) {
            log.error("컨테이너 제거 실패: {}", containerId, e);
            return false;
        }
    }

    /** 컨테이너 중지 + 제거 */
    public boolean stopAndRemove(String containerId) {
        return stop(containerId) && remove(containerId);
    }

    /** 컨테이너 이름 변경 */
    public boolean rename(String containerId, String newName) {
        try {
            dockerClient.renameContainerCmd(containerId).withName(newName).exec();
            return true;
        } catch (Exception e) {
            log.error("컨테이너 이름 변경 실패: {} → {}", containerId, newName, e);
            return false;
        }
    }

    /** 컨테이너 실행 중 여부 확인 */
    public boolean isRunning(String containerId) {
        try {
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
            Boolean running = inspect.getState().getRunning();
            return Boolean.TRUE.equals(running);
        } catch (Exception e) {
            return false;
        }
    }
}
