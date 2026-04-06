package com.lite_k8s.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ContainerConfig;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DockerServiceEnvVarsTest {

    @Mock private DockerClient dockerClient;
    @Mock private NodeRegistry nodeRegistry;
    @Mock private NodeDockerClientFactory nodeClientFactory;
    @Mock private InspectContainerCmd inspectCmd;
    @Mock private InspectContainerResponse inspectResponse;
    @Mock private ContainerConfig containerConfig;

    private DockerService dockerService;

    @BeforeEach
    void setUp() {
        dockerService = new DockerService(dockerClient, nodeRegistry, nodeClientFactory);
    }

    @Test
    void getContainerEnvVars_컨테이너_환경변수를_반환한다() {
        String[] envArray = {"SPRING_PROFILES_ACTIVE=prod", "SERVER_PORT=8080", "DB_URL=jdbc:postgresql://localhost/db"};

        when(dockerClient.inspectContainerCmd("abc123")).thenReturn(inspectCmd);
        when(inspectCmd.exec()).thenReturn(inspectResponse);
        when(inspectResponse.getConfig()).thenReturn(containerConfig);
        when(containerConfig.getEnv()).thenReturn(envArray);

        List<String> envVars = dockerService.getContainerEnvVars("abc123", null);

        assertThat(envVars).containsExactly(
                "SPRING_PROFILES_ACTIVE=prod",
                "SERVER_PORT=8080",
                "DB_URL=jdbc:postgresql://localhost/db"
        );
    }

    @Test
    void getContainerEnvVars_환경변수가_없으면_빈_리스트를_반환한다() {
        when(dockerClient.inspectContainerCmd("abc123")).thenReturn(inspectCmd);
        when(inspectCmd.exec()).thenReturn(inspectResponse);
        when(inspectResponse.getConfig()).thenReturn(containerConfig);
        when(containerConfig.getEnv()).thenReturn(null);

        List<String> envVars = dockerService.getContainerEnvVars("abc123", null);

        assertThat(envVars).isEmpty();
    }
}
