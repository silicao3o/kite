package com.lite_k8s.repository;

import com.lite_k8s.model.EmailSubscriptionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class EmailSubscriptionRepositoryTest {

    @Autowired
    private EmailSubscriptionRepository repository;

    @Test
    void shouldSaveAndFind() {
        repository.save(sub("alice@example.com", "web-*", null, true));

        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void shouldFindByEnabledTrue() {
        repository.save(sub("alice@example.com", "web-*", null, true));
        repository.save(sub("bob@example.com", null, "worker-1", false));
        repository.save(sub("charlie@example.com", "api-*", "worker-2", true));

        List<EmailSubscriptionEntity> active = repository.findByEnabled(true);

        assertThat(active).hasSize(2);
        assertThat(active).allMatch(EmailSubscriptionEntity::isEnabled);
    }

    private EmailSubscriptionEntity sub(String email, String pattern, String nodeName, boolean enabled) {
        return EmailSubscriptionEntity.builder()
                .email(email)
                .containerPattern(pattern)
                .nodeName(nodeName)
                .enabled(enabled)
                .build();
    }
}
