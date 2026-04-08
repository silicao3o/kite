package com.lite_k8s.service;

import com.lite_k8s.config.SelfHealingProperties;
import com.lite_k8s.model.SelfHealingRuleEntity;
import com.lite_k8s.repository.SelfHealingRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SelfHealingRuleServiceTest {

    @Mock
    private SelfHealingRuleRepository repository;

    @InjectMocks
    private SelfHealingRuleService service;

    private SelfHealingRuleEntity existing;

    @BeforeEach
    void setUp() {
        existing = SelfHealingRuleEntity.builder()
                .id("rule-1")
                .namePattern("engine*")
                .maxRestarts(5)
                .restartDelaySeconds(10)
                .enabled(true)
                .build();
    }

    @Test
    void findAllActive_shouldReturnEnabledRulesAsRuleObjects() {
        when(repository.findByEnabled(true)).thenReturn(List.of(existing));

        List<SelfHealingProperties.Rule> rules = service.findAllActive();

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getNamePattern()).isEqualTo("engine*");
        assertThat(rules.get(0).getMaxRestarts()).isEqualTo(5);
    }

    @Test
    void save_shouldDelegateToRepository() {
        when(repository.save(any(SelfHealingRuleEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        SelfHealingRuleEntity input = SelfHealingRuleEntity.builder()
                .namePattern("*chat*")
                .maxRestarts(3)
                .restartDelaySeconds(2)
                .enabled(true)
                .build();

        SelfHealingRuleEntity saved = service.save(input);

        assertThat(saved.getNamePattern()).isEqualTo("*chat*");
        verify(repository).save(input);
    }

    @Test
    void disable_shouldSetEnabledFalseAndSave() {
        when(repository.findById("rule-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(SelfHealingRuleEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.disable("rule-1");

        assertThat(existing.isEnabled()).isFalse();
        verify(repository).save(existing);
    }

    @Test
    void findById_shouldDelegateToRepository() {
        when(repository.findById("rule-1")).thenReturn(Optional.of(existing));

        assertThat(service.findById("rule-1")).contains(existing);
    }

    @Test
    void findAll_shouldDelegateToRepository() {
        when(repository.findAll()).thenReturn(List.of(existing));

        assertThat(service.findAll()).containsExactly(existing);
    }
}
