package com.lite_k8s.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 이메일 수신자별 알림 구독 (Phase 7.17).
 *
 * 한 사람이 여러 구독을 가질 수 있으며, 다음 필드로 대상을 지정한다:
 *  - containerPattern: 컨테이너 이름 패턴 (와일드카드 `*` 지원, 예: "web-*", "*chat*")
 *  - nodeName: 노드 이름 (정확 매칭)
 *
 * 최소 하나는 반드시 지정되어야 하며, 둘 다 있으면 AND 조건
 * (해당 노드의 해당 패턴 컨테이너만 수신).
 */
@Entity
@Table(name = "email_subscriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSubscriptionEntity {

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String email;

    /** 컨테이너 이름 패턴 (nullable). 와일드카드 `*` 지원. */
    private String containerPattern;

    /** 노드 이름 (nullable). 정확 매칭. */
    private String nodeName;

    /** 의도적 종료(docker stop/kill)도 수신할지 여부. 기본 false */
    private boolean notifyIntentional;

    private boolean enabled;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 엔티티 유효성 검증.
     * - email 필수
     * - containerPattern과 nodeName 중 최소 하나 필수
     */
    @PrePersist
    public void validate() {
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("email은 필수입니다");
        }
        boolean patternEmpty = containerPattern == null || containerPattern.isBlank();
        boolean nodeEmpty = nodeName == null || nodeName.isBlank();
        if (patternEmpty && nodeEmpty) {
            throw new IllegalStateException(
                    "containerPattern 또는 nodeName 중 최소 하나는 지정해야 합니다");
        }
    }
}
