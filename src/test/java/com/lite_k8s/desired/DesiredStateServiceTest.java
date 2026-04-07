package com.lite_k8s.desired;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DesiredStateServiceTest {

    @Mock
    private DesiredServiceSpecRepository repository;

    private DesiredStateService service;

    @BeforeEach
    void setUp() {
        service = new DesiredStateService(repository);
    }

    @Test
    @DisplayName("findAllActive: enabled=true인 엔티티를 ServiceSpec으로 변환하여 반환")
    void findAllActive_ReturnsConvertedSpecs() {
        DesiredServiceSpecEntity e1 = DesiredServiceSpecEntity.builder()
                .id("id-1").name("engine").image("img:latest").replicas(2)
                .containerNamePrefix("engine").nodeName("res").enabled(true).build();
        DesiredServiceSpecEntity e2 = DesiredServiceSpecEntity.builder()
                .id("id-2").name("worker").image("img2:latest").replicas(1)
                .containerNamePrefix("worker").enabled(true).build();

        when(repository.findByEnabled(true)).thenReturn(List.of(e1, e2));

        List<DesiredStateProperties.ServiceSpec> specs = service.findAllActive();

        assertThat(specs).hasSize(2);
        assertThat(specs.get(0).getName()).isEqualTo("engine");
        assertThat(specs.get(0).getReplicas()).isEqualTo(2);
        assertThat(specs.get(0).getNodeName()).isEqualTo("res");
        assertThat(specs.get(1).getName()).isEqualTo("worker");
    }

    @Test
    @DisplayName("findAllActive: DB가 비어있으면 빈 리스트 반환")
    void findAllActive_WhenEmpty_ReturnsEmptyList() {
        when(repository.findByEnabled(true)).thenReturn(List.of());

        assertThat(service.findAllActive()).isEmpty();
    }

    @Test
    @DisplayName("save: 새 스펙 저장 후 반환")
    void save_PersistsAndReturnsEntity() {
        DesiredServiceSpecEntity entity = DesiredServiceSpecEntity.builder()
                .id("id-1").name("api").image("img:latest").replicas(1)
                .containerNamePrefix("api").build();

        when(repository.save(entity)).thenReturn(entity);

        DesiredServiceSpecEntity saved = service.save(entity);

        assertThat(saved.getName()).isEqualTo("api");
        verify(repository).save(entity);
    }

    @Test
    @DisplayName("disable: enabled=false로 soft delete")
    void disable_SetsEnabledFalse() {
        DesiredServiceSpecEntity entity = DesiredServiceSpecEntity.builder()
                .id("id-1").name("api").image("img:latest").replicas(1)
                .containerNamePrefix("api").enabled(true).build();

        when(repository.findById("id-1")).thenReturn(java.util.Optional.of(entity));
        when(repository.save(any())).thenReturn(entity);

        service.disable("id-1");

        verify(repository).save(argThat(e -> !e.isEnabled()));
    }
}
