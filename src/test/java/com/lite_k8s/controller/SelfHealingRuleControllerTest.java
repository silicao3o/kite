package com.lite_k8s.controller;

import com.lite_k8s.model.SelfHealingRuleEntity;
import com.lite_k8s.service.SelfHealingRuleService;
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
class SelfHealingRuleControllerTest {

    @Mock
    private SelfHealingRuleService service;

    private SelfHealingRuleController controller;

    @BeforeEach
    void setUp() {
        controller = new SelfHealingRuleController(service);
    }

    @Test
    void create_shouldPersistNewRuleWithEnabledTrue() {
        when(service.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of(
                "namePattern", "engine*",
                "maxRestarts", 5,
                "restartDelaySeconds", 10,
                "nodeName", "res"
        );

        ResponseEntity<SelfHealingRuleEntity> response = controller.create(body);

        assertThat(response.getStatusCode().value()).isEqualTo(201);

        ArgumentCaptor<SelfHealingRuleEntity> captor = ArgumentCaptor.forClass(SelfHealingRuleEntity.class);
        verify(service).save(captor.capture());
        SelfHealingRuleEntity saved = captor.getValue();
        assertThat(saved.getNamePattern()).isEqualTo("engine*");
        assertThat(saved.getMaxRestarts()).isEqualTo(5);
        assertThat(saved.getRestartDelaySeconds()).isEqualTo(10);
        assertThat(saved.getNodeName()).isEqualTo("res");
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void list_shouldReturnAllRules() {
        SelfHealingRuleEntity rule = SelfHealingRuleEntity.builder().namePattern("a*").build();
        when(service.findAll()).thenReturn(List.of(rule));

        List<SelfHealingRuleEntity> result = controller.list();

        assertThat(result).containsExactly(rule);
    }

    @Test
    void update_shouldModifyExistingRuleFields() {
        SelfHealingRuleEntity existing = SelfHealingRuleEntity.builder()
                .id("rule-1")
                .namePattern("old*")
                .maxRestarts(1)
                .restartDelaySeconds(0)
                .enabled(true)
                .build();
        when(service.findById("rule-1")).thenReturn(Optional.of(existing));
        when(service.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of(
                "namePattern", "new*",
                "maxRestarts", 9,
                "restartDelaySeconds", 15,
                "nodeName", "worker"
        );

        ResponseEntity<SelfHealingRuleEntity> response = controller.update("rule-1", body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(existing.getNamePattern()).isEqualTo("new*");
        assertThat(existing.getMaxRestarts()).isEqualTo(9);
        assertThat(existing.getRestartDelaySeconds()).isEqualTo(15);
        assertThat(existing.getNodeName()).isEqualTo("worker");
        verify(service).save(existing);
    }

    @Test
    void update_shouldReturn404WhenNotFound() {
        when(service.findById("missing")).thenReturn(Optional.empty());

        ResponseEntity<SelfHealingRuleEntity> response = controller.update("missing", Map.of());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void delete_shouldSoftDeleteViaDisable() {
        ResponseEntity<Void> response = controller.delete("rule-1");

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(service).disable("rule-1");
    }
}
