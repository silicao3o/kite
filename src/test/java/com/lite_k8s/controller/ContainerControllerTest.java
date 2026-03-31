package com.lite_k8s.controller;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import com.lite_k8s.service.ContainerRecreateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContainerControllerTest {

    @Mock private ContainerRecreateService containerRecreateService;
    @Mock private DockerClient dockerClient;
    @Mock private NodeRegistry nodeRegistry;
    @Mock private NodeDockerClientFactory nodeClientFactory;

    private ContainerController controller;

    @BeforeEach
    void setUp() {
        controller = new ContainerController(containerRecreateService, dockerClient, nodeRegistry, nodeClientFactory);
    }

    @Test
    @DisplayName("update-image 성공 시 200 OK를 반환한다")
    void shouldReturn200WhenUpdateSucceeds() {
        doNothing().when(containerRecreateService).pullAndRecreate("abc123", null);

        ResponseEntity<String> response = controller.updateImage("abc123", null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("update-image 실패 시 500을 반환한다")
    void shouldReturn500WhenUpdateFails() {
        doThrow(new RuntimeException("이미지 없음")).when(containerRecreateService).pullAndRecreate("abc123", null);

        ResponseEntity<String> response = controller.updateImage("abc123", null);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).contains("이미지 없음");
    }
}
