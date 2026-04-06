package com.lite_k8s.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerInfoEnvVarsTest {

    @Test
    void envVars_필드가_존재하고_빌더로_설정할_수_있다() {
        List<String> envVars = List.of("SPRING_PROFILES_ACTIVE=prod", "SERVER_PORT=8080");

        ContainerInfo container = ContainerInfo.builder()
                .id("abc123")
                .name("test-container")
                .envVars(envVars)
                .build();

        assertThat(container.getEnvVars()).containsExactly(
                "SPRING_PROFILES_ACTIVE=prod",
                "SERVER_PORT=8080"
        );
    }

    @Test
    void envVars_필드가_null일_때_정상_동작한다() {
        ContainerInfo container = ContainerInfo.builder()
                .id("abc123")
                .name("test-container")
                .build();

        assertThat(container.getEnvVars()).isNull();
    }
}
