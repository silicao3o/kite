package com.lite_k8s.service;

import com.lite_k8s.model.ContainerInfo;
import com.lite_k8s.node.*;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DockerServiceMultiNodeTest {

    @Mock private DockerClient localDockerClient;
    @Mock private DockerClient nodeDockerClient;
    @Mock private NodeRegistry nodeRegistry;
    @Mock private NodeDockerClientFactory nodeClientFactory;
    @Mock private ListContainersCmd localListCmd;
    @Mock private ListContainersCmd nodeListCmd;

    private DockerService dockerService;

    @BeforeEach
    void setUp() {
        dockerService = new DockerService(localDockerClient, nodeRegistry, nodeClientFactory);
        ReflectionTestUtils.setField(dockerService, "logTailLines", 50);
    }

    @Test
    @DisplayName("노드가 없으면 로컬 컨테이너만 반환")
    void listContainers_NoNodes_ReturnsLocalOnly() {
        Container localContainer = mockContainer("local-abc", "local-app", "running");

        when(nodeRegistry.findAll()).thenReturn(List.of());
        when(localDockerClient.listContainersCmd()).thenReturn(localListCmd);
        when(localListCmd.withShowAll(true)).thenReturn(localListCmd);
        when(localListCmd.exec()).thenReturn(List.of(localContainer));

        List<ContainerInfo> result = dockerService.listContainers(true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeName()).isEqualTo("local");
    }

    @Test
    @DisplayName("노드 등록 시 노드 컨테이너도 포함")
    void listContainers_WithNode_ReturnsBothLocalAndNodeContainers() {
        Container localContainer = mockContainer("local-abc", "local-app", "running");
        Container nodeContainer = mockContainer("node-def", "node-app", "running");

        Node node = Node.builder()
                .id("node-1")
                .name("gcp-vm")
                .host("10.178.0.15")
                .port(2375)
                .build();

        when(nodeRegistry.findAll()).thenReturn(List.of(node));
        when(localDockerClient.listContainersCmd()).thenReturn(localListCmd);
        when(localListCmd.withShowAll(true)).thenReturn(localListCmd);
        when(localListCmd.exec()).thenReturn(List.of(localContainer));

        when(nodeClientFactory.createClient(node)).thenReturn(nodeDockerClient);
        when(nodeDockerClient.listContainersCmd()).thenReturn(nodeListCmd);
        when(nodeListCmd.withShowAll(true)).thenReturn(nodeListCmd);
        when(nodeListCmd.exec()).thenReturn(List.of(nodeContainer));

        List<ContainerInfo> result = dockerService.listContainers(true);

        assertThat(result).hasSize(2);
        assertThat(result).anyMatch(c -> c.getNodeName().equals("local"));
        assertThat(result).anyMatch(c -> c.getNodeName().equals("gcp-vm"));
    }

    @Test
    @DisplayName("노드 조회 실패 시 해당 노드는 건너뛰고 나머지 반환")
    void listContainers_NodeFails_SkipsFailedNode() {
        Container localContainer = mockContainer("local-abc", "local-app", "running");

        Node failingNode = Node.builder()
                .id("node-fail")
                .name("broken-node")
                .host("10.0.0.99")
                .port(2375)
                .build();

        when(nodeRegistry.findAll()).thenReturn(List.of(failingNode));
        when(localDockerClient.listContainersCmd()).thenReturn(localListCmd);
        when(localListCmd.withShowAll(true)).thenReturn(localListCmd);
        when(localListCmd.exec()).thenReturn(List.of(localContainer));

        when(nodeClientFactory.createClient(failingNode)).thenThrow(new RuntimeException("연결 실패"));

        List<ContainerInfo> result = dockerService.listContainers(true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeName()).isEqualTo("local");
    }

    private Container mockContainer(String id, String name, String state) {
        Container c = mock(Container.class);
        when(c.getId()).thenReturn(id);
        when(c.getNames()).thenReturn(new String[]{"/" + name});
        when(c.getState()).thenReturn(state);
        when(c.getStatus()).thenReturn(state);
        when(c.getImage()).thenReturn("test-image");
        when(c.getCreated()).thenReturn(0L);
        when(c.getPorts()).thenReturn(new com.github.dockerjava.api.model.ContainerPort[]{});
        return c;
    }
}
