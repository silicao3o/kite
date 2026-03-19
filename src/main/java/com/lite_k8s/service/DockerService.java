package com.lite_k8s.service;

import com.lite_k8s.model.ContainerDeathEvent;
import com.lite_k8s.model.ContainerInfo;
import com.lite_k8s.util.DockerContainerNames;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DockerService {

    private final DockerClient dockerClient;

    @Value("${docker.monitor.log-tail-lines:50}")
    private int logTailLines;

    public ContainerDeathEvent buildDeathEvent(String containerId, String action) {
        try {
            InspectContainerResponse inspection = dockerClient.inspectContainerCmd(containerId).exec();
            InspectContainerResponse.ContainerState state = inspection.getState();

            String containerName = DockerContainerNames.stripLeadingSlash(inspection.getName());

            Long exitCode = state.getExitCodeLong();
            Boolean oomKilled = state.getOOMKilled();
            String finishedAt = state.getFinishedAt();

            LocalDateTime deathTime = parseDockerTime(finishedAt);
            String lastLogs = getContainerLogs(containerId);

            return ContainerDeathEvent.builder()
                    .containerId(containerId)
                    .containerName(containerName)
                    .imageName(inspection.getConfig().getImage())
                    .deathTime(deathTime)
                    .exitCode(exitCode)
                    .oomKilled(oomKilled != null && oomKilled)
                    .action(action)
                    .lastLogs(lastLogs)
                    .labels(inspection.getConfig().getLabels())
                    .build();

        } catch (Exception e) {
            log.error("컨테이너 정보 조회 실패: {}", containerId, e);
            return ContainerDeathEvent.builder()
                    .containerId(containerId)
                    .containerName("Unknown")
                    .deathTime(LocalDateTime.now())
                    .action(action)
                    .lastLogs("로그 조회 실패: " + e.getMessage())
                    .build();
        }
    }

    public String getContainerLogs(String containerId) {
        try {
            List<String> logs = new ArrayList<>();

            LogContainerResultCallback callback = new LogContainerResultCallback() {
                @Override
                public void onNext(Frame frame) {
                    logs.add(new String(frame.getPayload()).trim());
                }
            };

            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(logTailLines)
                    .withTimestamps(true)
                    .exec(callback)
                    .awaitCompletion(10, TimeUnit.SECONDS);

            return String.join("\n", logs);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("로그 조회 중단: {}", containerId);
            return "로그 조회 중단";
        } catch (Exception e) {
            log.error("로그 조회 실패: {}", containerId, e);
            return "로그 조회 실패: " + e.getMessage();
        }
    }

    private LocalDateTime parseDockerTime(String dockerTime) {
        if (dockerTime == null || dockerTime.isEmpty() || dockerTime.equals("0001-01-01T00:00:00Z")) {
            return LocalDateTime.now();
        }
        try {
            Instant instant = Instant.parse(dockerTime);
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    public List<ContainerInfo> listContainers(boolean showAll) {
        List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(showAll)
                .exec();

        return containers.stream()
                .map(this::toContainerInfo)
                .toList();
    }

    public ContainerInfo getContainer(String containerId) {
        try {
            InspectContainerResponse inspection = dockerClient.inspectContainerCmd(containerId).exec();
            return toContainerInfo(inspection);
        } catch (Exception e) {
            log.error("컨테이너 조회 실패: {}", containerId, e);
            return null;
        }
    }

    private ContainerInfo toContainerInfo(Container container) {
        String name = DockerContainerNames.extractName(container, "unknown");

        List<ContainerInfo.PortMapping> ports = new ArrayList<>();
        if (container.getPorts() != null) {
            for (ContainerPort port : container.getPorts()) {
                ports.add(ContainerInfo.PortMapping.builder()
                        .privatePort(port.getPrivatePort() != null ? port.getPrivatePort() : 0)
                        .publicPort(port.getPublicPort() != null ? port.getPublicPort() : 0)
                        .type(port.getType() != null ? port.getType() : "tcp")
                        .build());
            }
        }

        return ContainerInfo.builder()
                .id(container.getId())
                .shortId(container.getId().substring(0, Math.min(12, container.getId().length())))
                .name(name)
                .image(container.getImage())
                .status(container.getStatus())
                .state(container.getState())
                .created(LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(container.getCreated()),
                        ZoneId.systemDefault()))
                .ports(ports)
                .labels(container.getLabels())
                .build();
    }

    public boolean restartContainer(String containerId) {
        return restartContainer(containerId, dockerClient);
    }

    public boolean restartContainer(String containerId, DockerClient client) {
        try {
            client.startContainerCmd(containerId).exec();
            log.info("컨테이너 재시작 성공: {}", containerId);
            return true;
        } catch (com.github.dockerjava.api.exception.NotModifiedException e) {
            log.info("컨테이너 이미 실행 중 (재시작 불필요): {}", containerId);
            return true;
        } catch (Exception e) {
            log.error("컨테이너 재시작 실패: {}", containerId, e);
            return false;
        }
    }

    private ContainerInfo toContainerInfo(InspectContainerResponse inspection) {
        String name = DockerContainerNames.stripLeadingSlash(inspection.getName());

        InspectContainerResponse.ContainerState state = inspection.getState();

        return ContainerInfo.builder()
                .id(inspection.getId())
                .shortId(inspection.getId().substring(0, Math.min(12, inspection.getId().length())))
                .name(name)
                .image(inspection.getConfig().getImage())
                .status(state.getStatus())
                .state(state.getStatus())
                .created(parseDockerTime(inspection.getCreated()))
                .ports(new ArrayList<>())
                .labels(inspection.getConfig().getLabels())
                .build();
    }
}
