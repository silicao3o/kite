package com.lite_k8s.repository;

import com.lite_k8s.model.NotificationRuleEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NotificationRuleRepositoryTest {

    @Autowired
    private NotificationRuleRepository repository;

    @Test
    void shouldSaveAndFind() {
        repository.save(rule("web-*", NotificationRuleEntity.Mode.INCLUDE, true));

        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void shouldFindByEnabledTrue() {
        repository.save(rule("web-*", NotificationRuleEntity.Mode.INCLUDE, true));
        repository.save(rule("*-temp", NotificationRuleEntity.Mode.EXCLUDE, false));
        repository.save(rule("*-chat", NotificationRuleEntity.Mode.EXCLUDE, true));

        List<NotificationRuleEntity> active = repository.findByEnabled(true);

        assertThat(active).hasSize(2);
        assertThat(active).allMatch(NotificationRuleEntity::isEnabled);
    }

    private NotificationRuleEntity rule(String pattern, NotificationRuleEntity.Mode mode, boolean enabled) {
        return NotificationRuleEntity.builder()
                .namePattern(pattern)
                .mode(mode)
                .enabled(enabled)
                .build();
    }
}
