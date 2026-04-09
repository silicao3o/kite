package com.lite_k8s.service;

import com.lite_k8s.model.EmailSubscriptionEntity;
import com.lite_k8s.repository.EmailSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 이메일 알림 구독 라우팅 (Phase 7.17).
 *
 * findRecipientsFor: 주어진 컨테이너/노드 이벤트에 대해 수신해야 하는 구독자 이메일 집합 반환.
 *
 * 매칭 규칙:
 *  - containerPattern이 있으면 와일드카드(`*`) 매칭
 *  - nodeName이 있으면 정확 매칭
 *  - 둘 다 있으면 AND 조건 (모두 만족해야 매칭)
 *  - intentional 이벤트는 notifyIntentional=true인 구독만 매칭
 *  - 같은 이메일이 여러 구독으로 매칭되어도 Set으로 중복 제거
 */
@Service
@RequiredArgsConstructor
public class EmailSubscriptionService {

    private final EmailSubscriptionRepository repository;

    public Set<String> findRecipientsFor(String containerName, String nodeName, boolean intentional) {
        return repository.findByEnabled(true).stream()
                .filter(sub -> intentional ? sub.isNotifyIntentional() : true)
                .filter(sub -> matches(sub, containerName, nodeName))
                .map(EmailSubscriptionEntity::getEmail)
                .collect(Collectors.toSet());
    }

    private boolean matches(EmailSubscriptionEntity sub, String containerName, String nodeName) {
        boolean hasPattern = sub.getContainerPattern() != null && !sub.getContainerPattern().isBlank();
        boolean hasNode = sub.getNodeName() != null && !sub.getNodeName().isBlank();

        if (hasPattern && hasNode) {
            return matchesPattern(containerName, sub.getContainerPattern())
                    && matchesNode(nodeName, sub.getNodeName());
        }
        if (hasPattern) {
            return matchesPattern(containerName, sub.getContainerPattern());
        }
        if (hasNode) {
            return matchesNode(nodeName, sub.getNodeName());
        }
        return false; // 둘 다 없음 (validation에서 걸러져야 하지만 방어)
    }

    private boolean matchesPattern(String containerName, String pattern) {
        if (containerName == null) return false;
        String regex = pattern.replace(".", "\\.").replace("*", ".*");
        return containerName.matches(regex);
    }

    private boolean matchesNode(String actualNodeName, String expectedNodeName) {
        if (actualNodeName == null) return false;
        return actualNodeName.equals(expectedNodeName);
    }

    // ========== CRUD ==========

    public EmailSubscriptionEntity save(EmailSubscriptionEntity entity) {
        entity.validate();
        return repository.save(entity);
    }

    public void disable(String id) {
        repository.findById(id).ifPresent(e -> {
            e.setEnabled(false);
            repository.save(e);
        });
    }

    public Optional<EmailSubscriptionEntity> findById(String id) {
        return repository.findById(id);
    }

    public List<EmailSubscriptionEntity> findAll() {
        // 비활성화된 구독은 화면에 표시하지 않음 (soft delete 의도와 일치)
        return repository.findByEnabled(true);
    }
}
