package com.lite_k8s.desired;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DesiredStateControllerTest {

    @Mock private DesiredStateService service;

    private DesiredStateController controller;

    @BeforeEach
    void setUp() {
        controller = new DesiredStateController(service);
    }

    @Test
    @DisplayName("POST /api/desired-state/services — 새 서비스 스펙 저장")
    void addService_SavesSpecAndReturnsResponse() {
        DesiredStateController.AddServiceRequest req = new DesiredStateController.AddServiceRequest();
        req.setName("engine");
        req.setImage("ghcr.io/myorg/engine:latest");
        req.setReplicas(2);
        req.setNodeName("res");

        DesiredServiceSpecEntity saved = DesiredServiceSpecEntity.builder()
                .id("uuid-1").name("engine").image("ghcr.io/myorg/engine:latest")
                .replicas(2).containerNamePrefix("engine").nodeName("res").build();
        when(service.save(any())).thenReturn(saved);

        DesiredStateController.ServiceSpecResponse response = controller.addService(req);

        ArgumentCaptor<DesiredServiceSpecEntity> captor = ArgumentCaptor.forClass(DesiredServiceSpecEntity.class);
        verify(service).save(captor.capture());

        DesiredServiceSpecEntity entity = captor.getValue();
        assertThat(entity.getName()).isEqualTo("engine");
        assertThat(entity.getImage()).isEqualTo("ghcr.io/myorg/engine:latest");
        assertThat(entity.getReplicas()).isEqualTo(2);
        assertThat(entity.getNodeName()).isEqualTo("res");
        assertThat(response.getId()).isEqualTo("uuid-1");
    }

    @Test
    @DisplayName("GET /api/desired-state/services — 저장된 서비스 목록 반환")
    void listServices_ReturnsAllEntities() {
        DesiredServiceSpecEntity e1 = DesiredServiceSpecEntity.builder()
                .id("id-1").name("engine").image("img:latest").replicas(1)
                .containerNamePrefix("engine").enabled(true).build();
        DesiredServiceSpecEntity e2 = DesiredServiceSpecEntity.builder()
                .id("id-2").name("worker").image("img2:latest").replicas(3)
                .containerNamePrefix("worker").enabled(false).build();

        when(service.findAll()).thenReturn(List.of(e1, e2));

        List<DesiredStateController.ServiceSpecResponse> responses = controller.listServices();

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getName()).isEqualTo("engine");
        assertThat(responses.get(1).getName()).isEqualTo("worker");
    }

    @Test
    @DisplayName("PUT /api/desired-state/services/{id} — replicas 수정")
    void updateService_UpdatesReplicasAndImage() {
        DesiredServiceSpecEntity existing = DesiredServiceSpecEntity.builder()
                .id("id-1").name("engine").image("img:latest").replicas(1)
                .containerNamePrefix("engine").enabled(true).build();

        DesiredStateController.UpdateServiceRequest req = new DesiredStateController.UpdateServiceRequest();
        req.setReplicas(5);
        req.setImage("img:v2");

        when(service.findById("id-1")).thenReturn(Optional.of(existing));
        when(service.save(any())).thenReturn(existing);

        controller.updateService("id-1", req);

        ArgumentCaptor<DesiredServiceSpecEntity> captor = ArgumentCaptor.forClass(DesiredServiceSpecEntity.class);
        verify(service).save(captor.capture());
        assertThat(captor.getValue().getReplicas()).isEqualTo(5);
        assertThat(captor.getValue().getImage()).isEqualTo("img:v2");
    }

    @Test
    @DisplayName("DELETE /api/desired-state/services/{id} — soft delete")
    void deleteService_CallsDisable() {
        controller.deleteService("id-1");

        verify(service).disable("id-1");
    }
}
