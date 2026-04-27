package com.lite_k8s.compose;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ServiceDefinitionControllerTest {

    private ServiceDefinitionRepository repository;
    private ServiceDeployer deployer;
    private NodeRegistry nodeRegistry;
    private DockerClient dockerClient;
    private NodeDockerClientFactory nodeClientFactory;
    private ServiceDefinitionController controller;

    @BeforeEach
    void setUp() {
        repository = mock(ServiceDefinitionRepository.class);
        deployer = mock(ServiceDeployer.class);
        nodeRegistry = mock(NodeRegistry.class);
        dockerClient = mock(DockerClient.class);
        nodeClientFactory = mock(NodeDockerClientFactory.class);
        when(repository.save(any(ServiceDefinition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deployer.deployWithDefinitionId(any(), any(), any(), any())).thenReturn("container-id");

        controller = new ServiceDefinitionController(
                repository,
                deployer,
                dockerClient,
                nodeRegistry,
                nodeClientFactory
        );
    }

    @Test
    @DisplayName("create — nodeEnvMappings 직접 전달")
    void createWithNodeEnvMappings() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "test-svc");
        body.put("composeYaml", "services:\n  app:\n    image: nginx");
        body.put("nodeEnvMappings", Map.of("node-01", "profile-a", "node-02", "profile-b"));

        ResponseEntity<?> response = controller.create(body);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        ServiceDefinition def = (ServiceDefinition) response.getBody();
        assertThat(def.getNodeEnvMappings()).hasSize(2);
        assertThat(def.getNodeEnvMappings().get("node-01")).isEqualTo("profile-a");
        assertThat(def.getNodeEnvMappings().get("node-02")).isEqualTo("profile-b");
    }

    @Test
    @DisplayName("create — 하위호환: envProfileId + nodeNames → nodeEnvMappings 변환")
    void createWithLegacyFormat() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "test-svc");
        body.put("composeYaml", "services:\n  app:\n    image: nginx");
        body.put("envProfileId", "profile-a");
        body.put("nodeNames", List.of("node-01", "node-02"));

        ResponseEntity<?> response = controller.create(body);

        ServiceDefinition def = (ServiceDefinition) response.getBody();
        assertThat(def.getNodeEnvMappings()).hasSize(2);
        assertThat(def.getNodeEnvMappings().get("node-01")).isEqualTo("profile-a");
        assertThat(def.getNodeEnvMappings().get("node-02")).isEqualTo("profile-a");
    }

    @Test
    @DisplayName("create — envProfileId만 있고 nodeNames 없으면 빈키로 매핑")
    void createWithProfileIdOnly() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "test-svc");
        body.put("composeYaml", "services:\n  app:\n    image: nginx");
        body.put("envProfileId", "profile-a");

        ResponseEntity<?> response = controller.create(body);

        ServiceDefinition def = (ServiceDefinition) response.getBody();
        assertThat(def.getNodeEnvMappings()).hasSize(1);
        assertThat(def.getNodeEnvMappings().get("")).isEqualTo("profile-a");
    }

    @Test
    @DisplayName("update — nodeEnvMappings 업데이트")
    void updateNodeEnvMappings() {
        ServiceDefinition existing = ServiceDefinition.builder()
                .name("test-svc")
                .composeYaml("services:\n  app:\n    image: nginx")
                .nodeEnvMappings(Map.of("node-01", "profile-a"))
                .build();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nodeEnvMappings", Map.of("node-02", "profile-b"));

        ResponseEntity<?> response = controller.update(existing.getId(), body);

        ServiceDefinition def = (ServiceDefinition) response.getBody();
        assertThat(def.getNodeEnvMappings()).hasSize(1);
        assertThat(def.getNodeEnvMappings().get("node-02")).isEqualTo("profile-b");
    }

    @Test
    @DisplayName("deploy — 각 노드별로 매핑된 profileId로 배포한다")
    void deployPerNodeWithMappedProfile() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("node-01", "profile-a");
        mappings.put("node-02", "profile-b");

        ServiceDefinition def = ServiceDefinition.builder()
                .name("test-svc")
                .composeYaml("services:\n  app:\n    image: nginx")
                .nodeEnvMappings(mappings)
                .build();
        when(repository.findById(def.getId())).thenReturn(Optional.of(def));

        Node node1 = new Node(); node1.setId("id-01"); node1.setName("node-01");
        Node node2 = new Node(); node2.setId("id-02"); node2.setName("node-02");
        when(nodeRegistry.findAll()).thenReturn(List.of(node1, node2));

        ResponseEntity<?> response = controller.deploy(def.getId(), null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        // deployer가 각 노드별로 호출되어야 함
        ArgumentCaptor<String> profileCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nodeCaptor = ArgumentCaptor.forClass(String.class);
        verify(deployer, times(2)).deployWithDefinitionId(any(), profileCaptor.capture(), nodeCaptor.capture(), eq(def.getId()));

        assertThat(profileCaptor.getAllValues()).containsExactly("profile-a", "profile-b");
        assertThat(nodeCaptor.getAllValues()).containsExactly("id-01", "id-02");
    }

    @Test
    @DisplayName("deploy — 매핑 비어있으면 로컬에 profileId=null로 배포")
    void deployLocalWhenNoMappings() {
        ServiceDefinition def = ServiceDefinition.builder()
                .name("test-svc")
                .composeYaml("services:\n  app:\n    image: nginx")
                .build();
        when(repository.findById(def.getId())).thenReturn(Optional.of(def));

        ResponseEntity<?> response = controller.deploy(def.getId(), null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(deployer, times(1)).deployWithDefinitionId(any(), eq(null), eq(null), eq(def.getId()));
    }

    @Test
    @DisplayName("create — nodeEnvMappings도 envProfileId도 없으면 빈 맵")
    void createWithNoMappings() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "test-svc");
        body.put("composeYaml", "services:\n  app:\n    image: nginx");

        ResponseEntity<?> response = controller.create(body);

        ServiceDefinition def = (ServiceDefinition) response.getBody();
        assertThat(def.getNodeEnvMappings()).isEmpty();
    }

    @Test
    @DisplayName("stop — 다중 노드의 컨테이너를 모두 정리한다")
    void stopCleansAllNodes() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("node-01", "profile-a");
        mappings.put("node-02", "profile-b");

        ServiceDefinition def = ServiceDefinition.builder()
                .name("test-svc")
                .composeYaml("services:\n  app:\n    image: nginx")
                .nodeEnvMappings(mappings)
                .status(ServiceDefinition.Status.DEPLOYED)
                .build();
        when(repository.findById(def.getId())).thenReturn(Optional.of(def));

        Node node1 = new Node(); node1.setId("id-01"); node1.setName("node-01");
        Node node2 = new Node(); node2.setId("id-02"); node2.setName("node-02");
        when(nodeRegistry.findAll()).thenReturn(List.of(node1, node2));

        // 노드별 Docker client mock
        DockerClient client1 = mockDockerClientWithContainers("c1");
        DockerClient client2 = mockDockerClientWithContainers("c2");
        when(nodeRegistry.findById("id-01")).thenReturn(Optional.of(node1));
        when(nodeRegistry.findById("id-02")).thenReturn(Optional.of(node2));
        when(nodeClientFactory.createClient(node1)).thenReturn(client1);
        when(nodeClientFactory.createClient(node2)).thenReturn(client2);

        ResponseEntity<?> response = controller.stop(def.getId());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(client1).removeContainerCmd("c1");
        verify(client2).removeContainerCmd("c2");
    }

    private DockerClient mockDockerClientWithContainers(String... containerIds) {
        DockerClient client = mock(DockerClient.class);
        ListContainersCmd listCmd = mock(ListContainersCmd.class);
        when(client.listContainersCmd()).thenReturn(listCmd);
        when(listCmd.withShowAll(true)).thenReturn(listCmd);
        when(listCmd.withLabelFilter(any(Map.class))).thenReturn(listCmd);

        List<Container> containers = new ArrayList<>();
        for (String cid : containerIds) {
            Container c = mock(Container.class);
            when(c.getId()).thenReturn(cid);
            when(c.getState()).thenReturn("running");
            containers.add(c);

            StopContainerCmd stopCmd = mock(StopContainerCmd.class);
            when(client.stopContainerCmd(cid)).thenReturn(stopCmd);
            RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
            when(client.removeContainerCmd(cid)).thenReturn(removeCmd);
        }
        when(listCmd.exec()).thenReturn(containers);
        return client;
    }
}
