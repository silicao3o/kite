package com.lite_k8s.service;

import com.lite_k8s.model.NotificationRuleEntity;
import com.lite_k8s.model.NotificationRuleEntity.Mode;
import com.lite_k8s.repository.NotificationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 알림 대상 컨테이너를 동적 규칙으로 판정한다 (Phase 7.15).
 *
 * 우선순위:
 *   1. 컨테이너 라벨 notification.enabled=false → 즉시 false (최우선)
 *   2. EXCLUDE 규칙 매칭 → false
 *   3. INCLUDE 규칙이 하나라도 존재:
 *        매칭 없음 → false
 *        매칭됨 → (intentional인 경우 notifyIntentional 체크)
 *   4. INCLUDE 규칙 없음 → (intentional인 경우 false, 아니면 true)
 *
 * intentional 처리:
 *   - intentional=false → 평소처럼 판정
 *   - intentional=true → 매칭된 INCLUDE 규칙의 notifyIntentional=true일 때만 알림
 */
@Service
@RequiredArgsConstructor
public class NotificationRuleService {

    private static final String LABEL_NOTIFICATION_ENABLED = "notification.enabled";

    private final NotificationRuleRepository repository;

    public boolean shouldNotify(String containerName, String nodeName,
                                Map<String, String> labels, boolean intentional) {
        // 1. 라벨 체크 (최우선)
        if (labels != null && "false".equalsIgnoreCase(labels.get(LABEL_NOTIFICATION_ENABLED))) {
            return false;
        }

        List<NotificationRuleEntity> rules = repository.findByEnabled(true);

        // 2. EXCLUDE 규칙 체크
        for (NotificationRuleEntity rule : rules) {
            if (rule.getMode() == Mode.EXCLUDE
                    && nodeApplies(rule, nodeName)
                    && matchesPattern(containerName, rule.getNamePattern())) {
                return false;
            }
        }

        // 3/4. INCLUDE 규칙 매칭 여부
        List<NotificationRuleEntity> includeRules = rules.stream()
                .filter(r -> r.getMode() == Mode.INCLUDE)
                .toList();

        if (includeRules.isEmpty()) {
            // INCLUDE 규칙이 없으면 기본 허용. intentional이면 기본 차단.
            return !intentional;
        }

        Optional<NotificationRuleEntity> matched = includeRules.stream()
                .filter(r -> nodeApplies(r, nodeName))
                .filter(r -> matchesPattern(containerName, r.getNamePattern()))
                .findFirst();

        if (matched.isEmpty()) {
            return false;
        }

        if (intentional) {
            return matched.get().isNotifyIntentional();
        }
        return true;
    }

    public NotificationRuleEntity save(NotificationRuleEntity entity) {
        return repository.save(entity);
    }

    public void disable(String id) {
        repository.findById(id).ifPresent(e -> {
            e.setEnabled(false);
            repository.save(e);
        });
    }

    public Optional<NotificationRuleEntity> findById(String id) {
        return repository.findById(id);
    }

    public List<NotificationRuleEntity> findAll() {
        return repository.findAll();
    }

    private boolean nodeApplies(NotificationRuleEntity rule, String nodeName) {
        if (rule.getNodeName() == null) return true;
        return rule.getNodeName().equals(nodeName);
    }

    private boolean matchesPattern(String containerName, String pattern) {
        if (pattern == null || containerName == null) return false;
        String regex = pattern.replace(".", "\\.").replace("*", ".*");
        return containerName.matches(regex);
    }
}
