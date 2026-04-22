package com.lite_k8s.compose;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.HostConfig;
import com.lite_k8s.envprofile.EnvProfileResolver;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceDeployerTest {

    @Mock private DockerClient dockerClient;
    @Mock private EnvProfileResolver envProfileResolver;
    @Mock private NodeRegistry nodeRegistry;
    @Mock private NodeDockerClientFactory nodeClientFactory;
    @Mock private CreateContainerCmd createCmd;
    @Mock private StartContainerCmd startCmd;
    @Mock private CreateNetworkCmd createNetworkCmd;
    @Mock private CreateContainerResponse createResponse;
    @Mock private CreateNetworkResponse createNetworkResponse;

    private ServiceDeployer deployer;

    @BeforeEach
    void setUp() {
        deployer = new ServiceDeployer(dockerClient, envProfileResolver, nodeRegistry, nodeClientFactory);

        when(dockerClient.createContainerCmd(anyString())).thenReturn(createCmd);
        when(createCmd.withName(anyString())).thenReturn(createCmd);
        when(createCmd.withHostConfig(any())).thenReturn(createCmd);
        when(createCmd.withEnv(any(String[].class))).thenReturn(createCmd);
        when(createCmd.withEnv(any(List.class))).thenReturn(createCmd);
        when(createCmd.withLabels(any())).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(createResponse);
        when(createResponse.getId()).thenReturn("new-container-id");
        when(dockerClient.startContainerCmd(anyString())).thenReturn(startCmd);
    }

    @Test
    @DisplayName("ParsedService를 Docker API로 배포한다")
    void deploy_CreatesAndStartsContainer() {
        ParsedService svc = ParsedService.builder()
                .serviceName("quvi")
                .image("ghcr.io/daquv-qv/quvi:latest")
                .containerName("quvi-operia")
                .ports(List.of("8080:8080"))
                .volumes(List.of("/data/keys:/app/keys"))
                .environment(Map.of("TZ", "Asia/Seoul"))
                .networks(List.of())
                .restartPolicy("unless-stopped")
                .labels(Map.of("team", "daquv"))
                .build();

        deployer.deploy(svc, null, null);

        verify(dockerClient).createContainerCmd("ghcr.io/daquv-qv/quvi:latest");
        verify(createCmd).withName("quvi-operia");
        verify(dockerClient).startContainerCmd("new-container-id");
        verify(startCmd).exec();
    }

    @Test
    @DisplayName("envProfileId가 있으면 프로파일 env를 merge한다")
    void deploy_WithEnvProfile_MergesEnv() {
        ParsedService svc = ParsedService.builder()
                .serviceName("quvi")
                .image("ghcr.io/daquv-qv/quvi:latest")
                .containerName("quvi")
                .ports(List.of())
                .volumes(List.of())
                .environment(Map.of("TZ", "Asia/Seoul"))
                .networks(List.of())
                .restartPolicy(null)
                .labels(Map.of())
                .build();

        when(envProfileResolver.resolve(List.of("profile-1")))
                .thenReturn(Map.of("DB_HOST", "10.0.0.1", "DB_PORT", "5432"));

        deployer.deploy(svc, "profile-1", null);

        ArgumentCaptor<List<String>> envCaptor = ArgumentCaptor.forClass(List.class);
        verify(createCmd).withEnv(envCaptor.capture());

        List<String> env = envCaptor.getValue();
        assertThat(env).contains("TZ=Asia/Seoul", "DB_HOST=10.0.0.1", "DB_PORT=5432");
    }

    @Test
    @DisplayName("image 필드의 ${KEY} 변수가 env로 치환된다")
    void deploy_SubstitutesImageVariable() {
        ParsedService svc = ParsedService.builder()
                .serviceName("app")
                .image("${IMAGE_REPO}:${IMAGE_TAG}")
                .containerName("app")
                .ports(List.of())
                .volumes(List.of())
                .environment(Map.of("IMAGE_REPO", "ghcr.io/org/app", "IMAGE_TAG", "v2.0"))
                .networks(List.of())
                .restartPolicy(null)
                .labels(Map.of())
                .build();

        deployer.deploy(svc, null, null);

        verify(dockerClient).createContainerCmd("ghcr.io/org/app:v2.0");
    }

    @Test
    @DisplayName("container_name, volumes, ports의 ${KEY}도 치환된다")
    void deploy_SubstitutesAllFields() {
        ParsedService svc = ParsedService.builder()
                .serviceName("app")
                .image("myapp:latest")
                .containerName("${APP_NAME}-prod")
                .ports(List.of("${HOST_PORT}:8080"))
                .volumes(List.of("${DATA_DIR}:/app/data"))
                .environment(Map.of("APP_NAME", "quvi", "HOST_PORT", "9090", "DATA_DIR", "/opt/data"))
                .networks(List.of())
                .restartPolicy(null)
                .labels(Map.of())
                .build();

        deployer.deploy(svc, null, null);

        verify(createCmd).withName("quvi-prod");
    }

    @Test
    @DisplayName("배포 시 kite.service-definition-id 라벨이 자동 부착된다")
    void deploy_AddsKiteLabel() {
        ParsedService svc = ParsedService.builder()
                .serviceName("app")
                .image("myapp:latest")
                .containerName("app")
                .ports(List.of())
                .volumes(List.of())
                .environment(Map.of())
                .networks(List.of())
                .restartPolicy(null)
                .labels(Map.of())
                .build();

        deployer.deployWithDefinitionId(svc, null, null, "def-123");

        ArgumentCaptor<Map<String, String>> labelsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(createCmd).withLabels(labelsCaptor.capture());
        assertThat(labelsCaptor.getValue()).containsEntry("kite.service-definition-id", "def-123");
    }
}
