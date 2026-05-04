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
    @Mock private com.lite_k8s.envprofile.ImageRegistryRepository imageRegistryRepository;

    private ServiceDeployer deployer;

    @BeforeEach
    void setUp() {
        deployer = new ServiceDeployer(dockerClient, envProfileResolver, nodeRegistry, nodeClientFactory, imageRegistryRepository);

        // inspectImageCmd — 이미지 존재하는 것으로 mock
        var inspectImageCmd = mock(com.github.dockerjava.api.command.InspectImageCmd.class);
        when(dockerClient.inspectImageCmd(anyString())).thenReturn(inspectImageCmd);
        when(inspectImageCmd.exec()).thenReturn(mock(com.github.dockerjava.api.command.InspectImageResponse.class));

        // pullImageCmd — 기본은 성공 (callback.onComplete 만 호출). 실패 케이스 테스트는 개별 override.
        var pullCmd = mock(com.github.dockerjava.api.command.PullImageCmd.class);
        when(dockerClient.pullImageCmd(anyString())).thenReturn(pullCmd);
        when(pullCmd.withPlatform(anyString())).thenReturn(pullCmd);
        when(pullCmd.withAuthConfig(any())).thenReturn(pullCmd);
        when(pullCmd.exec(any())).thenAnswer(invocation -> {
            com.github.dockerjava.api.async.ResultCallback<com.github.dockerjava.api.model.PullResponseItem> cb
                    = invocation.getArgument(0);
            cb.onComplete();
            return cb;
        });

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
    @DisplayName("${KEY:-default} 구문에서 KEY가 없으면 기본값을 사용한다")
    void deploy_SubstitutesDefaultValue() {
        ParsedService svc = ParsedService.builder()
                .serviceName("app")
                .image("ghcr.io/org/app:${IMAGE_TAG:-latest}")
                .containerName("app")
                .ports(List.of())
                .volumes(List.of())
                .environment(Map.of())  // IMAGE_TAG 없음
                .networks(List.of())
                .restartPolicy(null)
                .labels(Map.of())
                .build();

        deployer.deploy(svc, null, null);

        verify(dockerClient).createContainerCmd("ghcr.io/org/app:latest");
    }

    @Test
    @DisplayName("${KEY:-default} 구문에서 KEY가 있으면 해당 값을 사용한다")
    void deploy_SubstitutesValueOverDefault() {
        ParsedService svc = ParsedService.builder()
                .serviceName("app")
                .image("ghcr.io/org/app:${IMAGE_TAG:-latest}")
                .containerName("app")
                .ports(List.of())
                .volumes(List.of())
                .environment(Map.of("IMAGE_TAG", "v3.0"))
                .networks(List.of())
                .restartPolicy(null)
                .labels(Map.of())
                .build();

        deployer.deploy(svc, null, null);

        verify(dockerClient).createContainerCmd("ghcr.io/org/app:v3.0");
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
    @DisplayName("env 값의 자기참조 ${KEY:-default}는 프로파일에 값이 없으면 default로 폴백한다")
    void deploy_EnvSelfReference_FallsBackToDefault() {
        ParsedService svc = ParsedService.builder()
                .serviceName("app")
                .image("myapp:latest")
                .containerName("app")
                .ports(List.of())
                .volumes(List.of())
                .environment(Map.of("HIKARI_SCHEMA", "${HIKARI_SCHEMA:-public}"))
                .networks(List.of())
                .restartPolicy(null)
                .labels(Map.of())
                .build();

        deployer.deploy(svc, null, null);

        ArgumentCaptor<List<String>> envCaptor = ArgumentCaptor.forClass(List.class);
        verify(createCmd).withEnv(envCaptor.capture());
        assertThat(envCaptor.getValue()).contains("HIKARI_SCHEMA=public");
    }

    @Test
    @DisplayName("env 값의 자기참조 ${KEY:-default}는 프로파일에 값이 있으면 프로파일 값을 사용한다")
    void deploy_EnvSelfReference_UsesProfileValue() {
        ParsedService svc = ParsedService.builder()
                .serviceName("app")
                .image("myapp:latest")
                .containerName("app")
                .ports(List.of())
                .volumes(List.of())
                .environment(Map.of("HIKARI_SCHEMA", "${HIKARI_SCHEMA:-public}"))
                .networks(List.of())
                .restartPolicy(null)
                .labels(Map.of())
                .build();

        when(envProfileResolver.resolve(List.of("profile-1")))
                .thenReturn(Map.of("HIKARI_SCHEMA", "operia"));

        deployer.deploy(svc, "profile-1", null);

        ArgumentCaptor<List<String>> envCaptor = ArgumentCaptor.forClass(List.class);
        verify(createCmd).withEnv(envCaptor.capture());
        assertThat(envCaptor.getValue()).contains("HIKARI_SCHEMA=operia");
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

    @Test
    @DisplayName("이미지 pull 콜백이 onError 를 받으면 deploy 가 RuntimeException 으로 실패한다 — silent failure 방지")
    void deploy_PullCallbackOnError_PropagatesAsRuntimeException() {
        // PullImageCmd 가 onError 를 즉시 호출하는 daemon 시나리오 (manifest not found, auth 실패 등)
        var pullCmd = mock(com.github.dockerjava.api.command.PullImageCmd.class);
        when(dockerClient.pullImageCmd(anyString())).thenReturn(pullCmd);
        when(pullCmd.withPlatform(anyString())).thenReturn(pullCmd);
        when(pullCmd.withAuthConfig(any())).thenReturn(pullCmd);
        when(pullCmd.exec(any())).thenAnswer(invocation -> {
            com.github.dockerjava.api.async.ResultCallback<com.github.dockerjava.api.model.PullResponseItem> cb
                    = invocation.getArgument(0);
            cb.onError(new RuntimeException("daemon: manifest unknown"));
            return cb;
        });

        ParsedService svc = ParsedService.builder()
                .serviceName("engine")
                .image("ghcr.io/daquv-core/engine:latest")
                .containerName("engine")
                .ports(List.of())
                .volumes(List.of())
                .environment(Map.of())
                .networks(List.of())
                .restartPolicy(null)
                .labels(Map.of())
                .build();

        assertThatThrownBy(() -> deployer.deploy(svc, null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("manifest unknown");

        // pull 실패 시 createContainer 까지 가면 안 됨 — fail-fast
        verify(dockerClient, never()).createContainerCmd(anyString());
    }
}
