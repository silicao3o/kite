package com.lite_k8s.service;

import com.lite_k8s.model.EmailSubscriptionEntity;
import com.lite_k8s.repository.EmailSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailSubscriptionServiceTest {

    @Mock
    private EmailSubscriptionRepository repository;

    @InjectMocks
    private EmailSubscriptionService service;

    // ========== findRecipientsFor ==========

    @Test
    void findRecipients_noSubscriptions_shouldReturnEmpty() {
        when(repository.findByEnabled(true)).thenReturn(List.of());

        Set<String> recipients = service.findRecipientsFor("web-1", "worker-1", false);

        assertThat(recipients).isEmpty();
    }

    @Test
    void findRecipients_containerPatternMatches_shouldIncludeEmail() {
        when(repository.findByEnabled(true)).thenReturn(List.of(
                sub("alice@example.com", "web-*", null, false)));

        Set<String> recipients = service.findRecipientsFor("web-1", "worker-1", false);

        assertThat(recipients).containsExactly("alice@example.com");
    }

    @Test
    void findRecipients_containerPatternDoesNotMatch_shouldNotInclude() {
        when(repository.findByEnabled(true)).thenReturn(List.of(
                sub("alice@example.com", "db-*", null, false)));

        Set<String> recipients = service.findRecipientsFor("web-1", "worker-1", false);

        assertThat(recipients).isEmpty();
    }

    @Test
    void findRecipients_nodeNameMatches_shouldIncludeEmail() {
        when(repository.findByEnabled(true)).thenReturn(List.of(
                sub("bob@example.com", null, "worker-1", false)));

        Set<String> recipients = service.findRecipientsFor("web-1", "worker-1", false);

        assertThat(recipients).containsExactly("bob@example.com");
    }

    @Test
    void findRecipients_nodeNameDoesNotMatch_shouldNotInclude() {
        when(repository.findByEnabled(true)).thenReturn(List.of(
                sub("bob@example.com", null, "worker-2", false)));

        Set<String> recipients = service.findRecipientsFor("web-1", "worker-1", false);

        assertThat(recipients).isEmpty();
    }

    @Test
    void findRecipients_bothPatternAndNode_shouldRequireAndCondition() {
        when(repository.findByEnabled(true)).thenReturn(List.of(
                sub("charlie@example.com", "api-*", "worker-2", false)));

        // api-* 매칭 + worker-2 매칭 → 포함
        assertThat(service.findRecipientsFor("api-gateway", "worker-2", false))
                .containsExactly("charlie@example.com");

        // api-* 매칭 but worker-1 → AND 위반 → 제외
        assertThat(service.findRecipientsFor("api-gateway", "worker-1", false))
                .isEmpty();

        // worker-2 매칭 but web-1 → AND 위반 → 제외
        assertThat(service.findRecipientsFor("web-1", "worker-2", false))
                .isEmpty();
    }

    @Test
    void findRecipients_multipleSubscriptionsForSameEmail_shouldDeduplicate() {
        when(repository.findByEnabled(true)).thenReturn(List.of(
                sub("alice@example.com", "web-*", null, false),
                sub("alice@example.com", null, "worker-1", false)));

        Set<String> recipients = service.findRecipientsFor("web-1", "worker-1", false);

        assertThat(recipients).containsExactly("alice@example.com"); // 1개만
    }

    @Test
    void findRecipients_multipleRecipients_shouldCombine() {
        when(repository.findByEnabled(true)).thenReturn(List.of(
                sub("alice@example.com", "web-*", null, false),
                sub("bob@example.com", null, "worker-1", false),
                sub("charlie@example.com", "db-*", null, false)));

        Set<String> recipients = service.findRecipientsFor("web-1", "worker-1", false);

        assertThat(recipients).containsExactlyInAnyOrder(
                "alice@example.com",
                "bob@example.com");
    }

    @Test
    void findRecipients_intentional_onlyIncludeNotifyIntentionalTrue() {
        when(repository.findByEnabled(true)).thenReturn(List.of(
                sub("alice@example.com", "web-*", null, false),     // notifyIntentional=false
                sub("bob@example.com", "web-*", null, true)));      // notifyIntentional=true

        Set<String> recipients = service.findRecipientsFor("web-1", "worker-1", true);

        assertThat(recipients).containsExactly("bob@example.com");
    }

    @Test
    void findRecipients_notIntentional_bothTypesReceive() {
        when(repository.findByEnabled(true)).thenReturn(List.of(
                sub("alice@example.com", "web-*", null, false),
                sub("bob@example.com", "web-*", null, true)));

        Set<String> recipients = service.findRecipientsFor("web-1", "worker-1", false);

        assertThat(recipients).containsExactlyInAnyOrder(
                "alice@example.com",
                "bob@example.com");
    }

    @Test
    void findRecipients_wildcardInContainerPattern() {
        when(repository.findByEnabled(true)).thenReturn(List.of(
                sub("alice@example.com", "*-api-*", null, false)));

        assertThat(service.findRecipientsFor("user-api-v1", "worker-1", false))
                .containsExactly("alice@example.com");
        assertThat(service.findRecipientsFor("web-1", "worker-1", false))
                .isEmpty();
    }

    // ========== CRUD ==========

    @Test
    void save_shouldDelegate() {
        EmailSubscriptionEntity input = sub("a@b.com", "x-*", null, false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmailSubscriptionEntity saved = service.save(input);

        assertThat(saved.getEmail()).isEqualTo("a@b.com");
        verify(repository).save(input);
    }

    @Test
    void disable_shouldSetEnabledFalse() {
        EmailSubscriptionEntity existing = sub("a@b.com", "x-*", null, false);
        existing.setId("sub-1");
        existing.setEnabled(true);
        when(repository.findById("sub-1")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.disable("sub-1");

        assertThat(existing.isEnabled()).isFalse();
        verify(repository).save(existing);
    }

    @Test
    void findById_shouldDelegate() {
        EmailSubscriptionEntity existing = sub("a@b.com", "x-*", null, false);
        when(repository.findById("sub-1")).thenReturn(Optional.of(existing));

        assertThat(service.findById("sub-1")).contains(existing);
    }

    @Test
    void findAll_shouldReturnOnlyEnabled() {
        // findAll은 화면 표시용 — 비활성화된 구독은 제외
        EmailSubscriptionEntity existing = sub("a@b.com", "x-*", null, false);
        when(repository.findByEnabled(true)).thenReturn(List.of(existing));

        assertThat(service.findAll()).containsExactly(existing);
        verify(repository).findByEnabled(true);
        verify(repository, never()).findAll();
    }

    private EmailSubscriptionEntity sub(String email, String pattern, String nodeName, boolean notifyIntentional) {
        return EmailSubscriptionEntity.builder()
                .email(email)
                .containerPattern(pattern)
                .nodeName(nodeName)
                .notifyIntentional(notifyIntentional)
                .enabled(true)
                .build();
    }
}
