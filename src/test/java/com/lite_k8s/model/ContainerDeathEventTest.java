package com.lite_k8s.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerDeathEventTest {

    @Test
    @DisplayName("ContainerDeathEvent는 nodeId를 포함한다")
    void shouldIncludeNodeId() {
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerId("c123")
                .containerName("test-app")
                .nodeId("node-vm-b")
                .build();

        assertThat(event.getNodeId()).isEqualTo("node-vm-b");
    }

    @Test
    @DisplayName("nodeId가 없으면 null이다 (로컬 단일 모드 호환)")
    void shouldAllowNullNodeId() {
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerId("c123")
                .containerName("test-app")
                .build();

        assertThat(event.getNodeId()).isNull();
    }
}
