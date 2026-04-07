package com.lite_k8s.desired;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DesiredServiceSpecEntityTest {

    @Test
    void shouldCreateEntityWithRequiredFields() {
        DesiredServiceSpecEntity entity = DesiredServiceSpecEntity.builder()
                .id("uuid-1")
                .name("engine")
                .image("ghcr.io/myorg/engine:latest")
                .replicas(2)
                .containerNamePrefix("engine")
                .build();

        assertThat(entity.getId()).isEqualTo("uuid-1");
        assertThat(entity.getName()).isEqualTo("engine");
        assertThat(entity.getImage()).isEqualTo("ghcr.io/myorg/engine:latest");
        assertThat(entity.getReplicas()).isEqualTo(2);
        assertThat(entity.getContainerNamePrefix()).isEqualTo("engine");
    }

    @Test
    void shouldDefaultToEnabled() {
        DesiredServiceSpecEntity entity = DesiredServiceSpecEntity.builder()
                .id("uuid-2")
                .name("demo-api")
                .image("ghcr.io/myorg/demo-api:latest")
                .replicas(1)
                .containerNamePrefix("demo-api")
                .build();

        assertThat(entity.isEnabled()).isTrue();
    }

    @Test
    void shouldSupportOptionalNodeName() {
        DesiredServiceSpecEntity entity = DesiredServiceSpecEntity.builder()
                .id("uuid-3")
                .name("engine")
                .image("ghcr.io/myorg/engine:latest")
                .replicas(1)
                .containerNamePrefix("engine")
                .nodeName("res")
                .build();

        assertThat(entity.getNodeName()).isEqualTo("res");
    }

    @Test
    void shouldSupportEnvAndPortsAsJson() {
        DesiredServiceSpecEntity entity = DesiredServiceSpecEntity.builder()
                .id("uuid-4")
                .name("api")
                .image("img:latest")
                .replicas(1)
                .containerNamePrefix("api")
                .envJson("[\"DB_HOST=postgres\",\"PORT=8080\"]")
                .portsJson("[\"8080:8080\"]")
                .build();

        assertThat(entity.getEnvJson()).contains("DB_HOST=postgres");
        assertThat(entity.getPortsJson()).contains("8080:8080");
    }

    @Test
    void shouldConvertToServiceSpec() {
        DesiredServiceSpecEntity entity = DesiredServiceSpecEntity.builder()
                .id("uuid-5")
                .name("engine")
                .image("ghcr.io/myorg/engine:latest")
                .replicas(3)
                .containerNamePrefix("engine")
                .nodeName("res")
                .envJson("[\"KEY=VAL\"]")
                .portsJson("[\"9090:9090\"]")
                .build();

        DesiredStateProperties.ServiceSpec spec = entity.toServiceSpec();

        assertThat(spec.getName()).isEqualTo("engine");
        assertThat(spec.getImage()).isEqualTo("ghcr.io/myorg/engine:latest");
        assertThat(spec.getReplicas()).isEqualTo(3);
        assertThat(spec.getContainerNamePrefix()).isEqualTo("engine");
        assertThat(spec.getNodeName()).isEqualTo("res");
        assertThat(spec.getEnv()).containsExactly("KEY=VAL");
        assertThat(spec.getPorts()).containsExactly("9090:9090");
    }
}
