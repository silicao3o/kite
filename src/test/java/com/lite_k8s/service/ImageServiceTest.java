package com.lite_k8s.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.RemoveImageCmd;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock private DockerClient dockerClient;
    @Mock private DockerClient nodeDockerClient;
    @Mock private NodeRegistry nodeRegistry;
    @Mock private NodeDockerClientFactory nodeClientFactory;
    @Mock private RemoveImageCmd removeImageCmd;
    @Mock private RemoveImageCmd nodeRemoveImageCmd;
    @Mock private Node node;

    private ImageService service;

    @BeforeEach
    void setUp() {
        service = new ImageService(dockerClient, nodeRegistry, nodeClientFactory);
    }

    @Test
    @DisplayName("nodeId가 null이면 로컬 클라이언트로 removeImageCmd를 실행한다")
    void shouldDeleteImageWithLocalClientWhenNodeIdIsNull() {
        when(dockerClient.removeImageCmd("nginx:latest")).thenReturn(removeImageCmd);

        service.deleteImage("nginx:latest", null);

        verify(dockerClient).removeImageCmd("nginx:latest");
        verify(removeImageCmd).exec();
    }

    @Test
    @DisplayName("nodeId가 있으면 해당 노드 클라이언트로 removeImageCmd를 실행한다")
    void shouldDeleteImageWithNodeClientWhenNodeIdProvided() {
        when(nodeRegistry.findById("node-1")).thenReturn(Optional.of(node));
        when(nodeClientFactory.createClient(node)).thenReturn(nodeDockerClient);
        when(nodeDockerClient.removeImageCmd("nginx:latest")).thenReturn(nodeRemoveImageCmd);

        service.deleteImage("nginx:latest", "node-1");

        verify(nodeDockerClient).removeImageCmd("nginx:latest");
        verify(nodeRemoveImageCmd).exec();
        verify(dockerClient, never()).removeImageCmd(any());
    }

    @Test
    @DisplayName("removeImageCmd 실행 중 예외 발생 시 그대로 전파된다")
    void shouldPropagateExceptionWhenRemoveImageFails() {
        when(dockerClient.removeImageCmd("nginx:latest")).thenReturn(removeImageCmd);
        doThrow(new RuntimeException("image is being used by container abc123"))
                .when(removeImageCmd).exec();

        assertThatThrownBy(() -> service.deleteImage("nginx:latest", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("image is being used by container abc123");
    }
}
