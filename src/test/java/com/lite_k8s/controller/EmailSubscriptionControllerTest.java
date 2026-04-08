package com.lite_k8s.controller;

import com.lite_k8s.model.EmailSubscriptionEntity;
import com.lite_k8s.service.EmailSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailSubscriptionControllerTest {

    @Mock
    private EmailSubscriptionService service;

    private EmailSubscriptionController controller;

    @BeforeEach
    void setUp() {
        controller = new EmailSubscriptionController(service);
    }

    @Test
    void create_withContainerPattern_shouldSave() {
        when(service.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of(
                "email", "alice@example.com",
                "containerPattern", "web-*",
                "notifyIntentional", false
        );

        ResponseEntity<?> response = controller.create(body);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        ArgumentCaptor<EmailSubscriptionEntity> captor = ArgumentCaptor.forClass(EmailSubscriptionEntity.class);
        verify(service).save(captor.capture());
        EmailSubscriptionEntity saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getContainerPattern()).isEqualTo("web-*");
        assertThat(saved.getNodeName()).isNull();
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void create_withNodeName_shouldSave() {
        when(service.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of(
                "email", "bob@example.com",
                "nodeName", "worker-1"
        );

        ResponseEntity<?> response = controller.create(body);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        ArgumentCaptor<EmailSubscriptionEntity> captor = ArgumentCaptor.forClass(EmailSubscriptionEntity.class);
        verify(service).save(captor.capture());
        assertThat(captor.getValue().getNodeName()).isEqualTo("worker-1");
    }

    @Test
    void create_withBothContainerAndNode_shouldSave() {
        when(service.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of(
                "email", "charlie@example.com",
                "containerPattern", "api-*",
                "nodeName", "worker-2",
                "notifyIntentional", true
        );

        controller.create(body);

        ArgumentCaptor<EmailSubscriptionEntity> captor = ArgumentCaptor.forClass(EmailSubscriptionEntity.class);
        verify(service).save(captor.capture());
        assertThat(captor.getValue().isNotifyIntentional()).isTrue();
    }

    @Test
    void create_withoutContainerAndNode_shouldReturn400() {
        Map<String, Object> body = Map.of("email", "invalid@example.com");

        ResponseEntity<?> response = controller.create(body);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(service, never()).save(any());
    }

    @Test
    void create_withBlankEmail_shouldReturn400() {
        Map<String, Object> body = new HashMap<>();
        body.put("email", "  ");
        body.put("containerPattern", "web-*");

        ResponseEntity<?> response = controller.create(body);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(service, never()).save(any());
    }

    @Test
    void list_shouldReturnAll() {
        EmailSubscriptionEntity entity = EmailSubscriptionEntity.builder()
                .email("a@b.com").containerPattern("x-*").enabled(true).build();
        when(service.findAll()).thenReturn(List.of(entity));

        List<EmailSubscriptionEntity> result = controller.list();

        assertThat(result).containsExactly(entity);
    }

    @Test
    void update_shouldModifyFields() {
        EmailSubscriptionEntity existing = EmailSubscriptionEntity.builder()
                .id("sub-1")
                .email("a@b.com")
                .containerPattern("old-*")
                .enabled(true)
                .build();
        when(service.findById("sub-1")).thenReturn(Optional.of(existing));
        when(service.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of(
                "containerPattern", "new-*",
                "nodeName", "worker-1",
                "notifyIntentional", true
        );

        ResponseEntity<?> response = controller.update("sub-1", body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(existing.getContainerPattern()).isEqualTo("new-*");
        assertThat(existing.getNodeName()).isEqualTo("worker-1");
        assertThat(existing.isNotifyIntentional()).isTrue();
    }

    @Test
    void update_shouldReturn404WhenNotFound() {
        when(service.findById("missing")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.update("missing", Map.of());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void delete_shouldCallDisable() {
        ResponseEntity<Void> response = controller.delete("sub-1");

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(service).disable("sub-1");
    }
}
