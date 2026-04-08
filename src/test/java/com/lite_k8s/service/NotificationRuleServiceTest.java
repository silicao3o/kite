package com.lite_k8s.service;

import com.lite_k8s.model.NotificationRuleEntity;
import com.lite_k8s.model.NotificationRuleEntity.Mode;
import com.lite_k8s.repository.NotificationRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationRuleServiceTest {

    @Mock
    private NotificationRuleRepository repository;

    @InjectMocks
    private NotificationRuleService service;

    // ===== shouldNotify — 우선순위 테스트 =====

    @Test
    void shouldNotify_noRules_shouldReturnTrue() {
        when(repository.findByEnabled(true)).thenReturn(List.of());

        boolean result = service.shouldNotify("web-1", "local", Map.of(), false);

        assertThat(result).isTrue();
    }

    @Test
    void shouldNotify_labelNotificationEnabledFalse_shouldReturnFalse() {
        // 라벨이 최우선 — 규칙 쿼리 이전에 단락됨
        boolean result = service.shouldNotify("web-1", "local",
                Map.of("notification.enabled", "false"), false);

        assertThat(result).isFalse();
        verify(repository, never()).findByEnabled(anyBoolean());
    }

    @Test
    void shouldNotify_excludeMatches_shouldReturnFalse() {
        when(repository.findByEnabled(true)).thenReturn(List.of(
                rule("*-temp", Mode.EXCLUDE, null, false)));

        boolean result = service.shouldNotify("job-temp", "local", Map.of(), false);

        assertThat(result).isFalse();
    }

    @Test
    void shouldNotify_includeMatches_shouldReturnTrue() {
        when(repository.findByEnabled(true)).thenReturn(List.of(
                rule("web-*", Mode.INCLUDE, null, false)));

        boolean result = service.shouldNotify("web-1", "local", Map.of(), false);

        assertThat(result).isTrue();
    }

    @Test
    void shouldNotify_includeExists_butNoMatch_shouldReturnFalse() {
        when(repository.findByEnabled(true)).thenReturn(List.of(
                rule("web-*", Mode.INCLUDE, null, false)));

        boolean result = service.shouldNotify("db-1", "local", Map.of(), false);

        assertThat(result).isFalse();
    }

    @Test
    void shouldNotify_excludeTakesPrecedenceOverInclude() {
        when(repository.findByEnabled(true)).thenReturn(List.of(
                rule("web-*", Mode.INCLUDE, null, false),
                rule("*-temp", Mode.EXCLUDE, null, false)));

        boolean result = service.shouldNotify("web-temp", "local", Map.of(), false);

        assertThat(result).isFalse();
    }

    @Test
    void shouldNotify_nodeNameMismatch_ruleDoesNotApply() {
        when(repository.findByEnabled(true)).thenReturn(List.of(
                rule("web-*", Mode.EXCLUDE, "worker-1", false)));

        // 다른 노드에서는 이 exclude 규칙이 적용되지 않음 → 기본 허용
        boolean result = service.shouldNotify("web-1", "worker-2", Map.of(), false);

        assertThat(result).isTrue();
    }

    @Test
    void shouldNotify_nodeNameNull_appliesToAllNodes() {
        when(repository.findByEnabled(true)).thenReturn(List.of(
                rule("web-*", Mode.EXCLUDE, null, false)));

        boolean result = service.shouldNotify("web-1", "worker-2", Map.of(), false);

        assertThat(result).isFalse();
    }

    // ===== intentional 처리 =====

    @Test
    void shouldNotify_intentional_noOverride_shouldReturnFalse() {
        // intentional이고 notifyIntentional=true 규칙이 없으면 알림 안 감
        when(repository.findByEnabled(true)).thenReturn(List.of());

        boolean result = service.shouldNotify("web-1", "local", Map.of(), true);

        assertThat(result).isFalse();
    }

    @Test
    void shouldNotify_intentional_withMatchingNotifyIntentionalRule_shouldReturnTrue() {
        // INCLUDE 규칙이 매칭되고 notifyIntentional=true면 intentional이어도 알림
        when(repository.findByEnabled(true)).thenReturn(List.of(
                rule("critical-*", Mode.INCLUDE, null, true)));

        boolean result = service.shouldNotify("critical-api", "local", Map.of(), true);

        assertThat(result).isTrue();
    }

    @Test
    void shouldNotify_intentional_matchingRuleButNotifyIntentionalFalse_shouldReturnFalse() {
        when(repository.findByEnabled(true)).thenReturn(List.of(
                rule("web-*", Mode.INCLUDE, null, false)));

        boolean result = service.shouldNotify("web-1", "local", Map.of(), true);

        assertThat(result).isFalse();
    }

    @Test
    void shouldNotify_notIntentional_normalFlow() {
        when(repository.findByEnabled(true)).thenReturn(List.of(
                rule("web-*", Mode.INCLUDE, null, false)));

        boolean result = service.shouldNotify("web-1", "local", Map.of(), false);

        assertThat(result).isTrue();
    }

    // ===== CRUD =====

    @Test
    void save_shouldDelegateToRepository() {
        NotificationRuleEntity input = rule("a*", Mode.INCLUDE, null, false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationRuleEntity saved = service.save(input);

        assertThat(saved.getNamePattern()).isEqualTo("a*");
        verify(repository).save(input);
    }

    @Test
    void disable_shouldSetEnabledFalseAndSave() {
        NotificationRuleEntity existing = rule("a*", Mode.INCLUDE, null, false);
        existing.setId("rule-1");
        existing.setEnabled(true);
        when(repository.findById("rule-1")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.disable("rule-1");

        assertThat(existing.isEnabled()).isFalse();
        verify(repository).save(existing);
    }

    @Test
    void findById_shouldDelegate() {
        NotificationRuleEntity existing = rule("a*", Mode.INCLUDE, null, false);
        when(repository.findById("rule-1")).thenReturn(Optional.of(existing));

        assertThat(service.findById("rule-1")).contains(existing);
    }

    @Test
    void findAll_shouldDelegate() {
        NotificationRuleEntity existing = rule("a*", Mode.INCLUDE, null, false);
        when(repository.findAll()).thenReturn(List.of(existing));

        assertThat(service.findAll()).containsExactly(existing);
    }

    private NotificationRuleEntity rule(String pattern, Mode mode, String nodeName, boolean notifyIntentional) {
        return NotificationRuleEntity.builder()
                .namePattern(pattern)
                .mode(mode)
                .nodeName(nodeName)
                .notifyIntentional(notifyIntentional)
                .enabled(true)
                .build();
    }
}
