package com.lite_k8s.desired;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DesiredServiceSpecRepositoryTest {

    @Mock
    private DesiredServiceSpecRepository repository;

    @Test
    @DisplayName("enabled=true인 스펙만 조회")
    void findByEnabled_ReturnsOnlyEnabledSpecs() {
        DesiredServiceSpecEntity e1 = DesiredServiceSpecEntity.builder()
                .id("id-1").name("engine").image("img:latest").replicas(1)
                .containerNamePrefix("engine").enabled(true).build();
        DesiredServiceSpecEntity e2 = DesiredServiceSpecEntity.builder()
                .id("id-2").name("worker").image("img2:latest").replicas(2)
                .containerNamePrefix("worker").enabled(true).build();

        when(repository.findByEnabled(true)).thenReturn(List.of(e1, e2));

        List<DesiredServiceSpecEntity> result = repository.findByEnabled(true);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(DesiredServiceSpecEntity::isEnabled);
    }

    @Test
    @DisplayName("이름으로 스펙 조회")
    void findByName_WhenExists_ReturnsSpec() {
        DesiredServiceSpecEntity entity = DesiredServiceSpecEntity.builder()
                .id("id-1").name("engine").image("img:latest").replicas(1)
                .containerNamePrefix("engine").build();

        when(repository.findByName("engine")).thenReturn(Optional.of(entity));

        Optional<DesiredServiceSpecEntity> result = repository.findByName("engine");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("engine");
    }

    @Test
    @DisplayName("존재하지 않는 이름 조회 시 empty")
    void findByName_WhenNotExists_ReturnsEmpty() {
        when(repository.findByName("unknown")).thenReturn(Optional.empty());

        assertThat(repository.findByName("unknown")).isEmpty();
    }
}
