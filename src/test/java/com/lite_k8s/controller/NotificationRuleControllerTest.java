package com.lite_k8s.controller;

import com.lite_k8s.model.NotificationRuleEntity;
import com.lite_k8s.service.NotificationRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationRuleControllerTest {

    @Mock
    private NotificationRuleService service;

    private NotificationRuleController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationRuleController(service);
    }

    @Test
    void create_shouldPersistWithEnabledTrue() {
        when(service.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of(
                "namePattern", "web-*",
                "mode", "INCLUDE",
                "nodeName", "local",
                "notifyIntentional", true
        );

        ResponseEntity<NotificationRuleEntity> response = controller.create(body);

        assertThat(response.getStatusCode().value()).isEqualTo(201);

        ArgumentCaptor<NotificationRuleEntity> captor = ArgumentCaptor.forClass(NotificationRuleEntity.class);
        verify(service).save(captor.capture());
        NotificationRuleEntity saved = captor.getValue();
        assertThat(saved.getNamePattern()).isEqualTo("web-*");
        assertThat(saved.getMode()).isEqualTo(NotificationRuleEntity.Mode.INCLUDE);
        assertThat(saved.getNodeName()).isEqualTo("local");
        assertThat(saved.isNotifyIntentional()).isTrue();
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void create_shouldDefaultModeToInclude() {
        when(service.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of("namePattern", "a*");

        controller.create(body);

        ArgumentCaptor<NotificationRuleEntity> captor = ArgumentCaptor.forClass(NotificationRuleEntity.class);
        verify(service).save(captor.capture());
        assertThat(captor.getValue().getMode()).isEqualTo(NotificationRuleEntity.Mode.INCLUDE);
    }

    @Test
    void list_shouldReturnAll() {
        NotificationRuleEntity rule = NotificationRuleEntity.builder().namePattern("a*").build();
        when(service.findAll()).thenReturn(List.of(rule));

        List<NotificationRuleEntity> result = controller.list();

        assertThat(result).containsExactly(rule);
    }

    @Test
    void update_shouldModifyExistingFields() {
        NotificationRuleEntity existing = NotificationRuleEntity.builder()
                .id("rule-1")
                .namePattern("old*")
                .mode(NotificationRuleEntity.Mode.INCLUDE)
                .notifyIntentional(false)
                .enabled(true)
                .build();
        when(service.findById("rule-1")).thenReturn(Optional.of(existing));
        when(service.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of(
                "namePattern", "new*",
                "mode", "EXCLUDE",
                "nodeName", "worker",
                "notifyIntentional", true
        );

        ResponseEntity<NotificationRuleEntity> response = controller.update("rule-1", body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(existing.getNamePattern()).isEqualTo("new*");
        assertThat(existing.getMode()).isEqualTo(NotificationRuleEntity.Mode.EXCLUDE);
        assertThat(existing.getNodeName()).isEqualTo("worker");
        assertThat(existing.isNotifyIntentional()).isTrue();
        verify(service).save(existing);
    }

    @Test
    void update_shouldReturn404WhenNotFound() {
        when(service.findById("missing")).thenReturn(Optional.empty());

        ResponseEntity<NotificationRuleEntity> response = controller.update("missing", Map.of());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void delete_shouldCallDisable() {
        ResponseEntity<Void> response = controller.delete("rule-1");

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(service).disable("rule-1");
    }
}
