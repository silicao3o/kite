package com.lite_k8s.repository;

import com.lite_k8s.model.SelfHealingRuleEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SelfHealingRuleRepositoryTest {

    @Autowired
    private SelfHealingRuleRepository repository;

    @Test
    @DisplayName("규칙을 저장하고 조회할 수 있다")
    void shouldSaveAndFind() {
        repository.save(rule("engine*", true));

        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("enabled=true 규칙만 조회한다")
    void shouldFindByEnabledTrue() {
        repository.save(rule("engine*", true));
        repository.save(rule("*chat*", false));
        repository.save(rule("*quvi*", true));

        List<SelfHealingRuleEntity> active = repository.findByEnabled(true);

        assertThat(active).hasSize(2);
        assertThat(active).allMatch(SelfHealingRuleEntity::isEnabled);
    }

    private SelfHealingRuleEntity rule(String pattern, boolean enabled) {
        return SelfHealingRuleEntity.builder()
                .namePattern(pattern)
                .maxRestarts(3)
                .restartDelaySeconds(5)
                .enabled(enabled)
                .build();
    }
}
