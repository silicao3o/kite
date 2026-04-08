package com.lite_k8s.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 알림 대상 컨테이너 선택 규칙 (DB 기반, Phase 7.15).
 *
 * INCLUDE 모드: 매칭되는 컨테이너만 알림 대상 (화이트리스트)
 * EXCLUDE 모드: 매칭되는 컨테이너는 알림 제외 (블랙리스트)
 *
 * 우선순위 (NotificationRuleService.shouldNotify):
 *   1. 컨테이너 라벨 notification.enabled=false → 즉시 false
 *   2. EXCLUDE 규칙 매칭 → false
 *   3. INCLUDE 규칙이 존재: 매칭 없으면 false, 매칭되면 true
 *   4. 둘 다 없음 → true (기본 허용)
 */
@Entity
@Table(name = "notification_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRuleEntity {

    public enum Mode {
        INCLUDE,
        EXCLUDE
    }

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String namePattern;

    /** 특정 노드에만 적용. null이면 모든 노드 */
    private String nodeName;

    @Enumerated(EnumType.STRING)
    private Mode mode;

    /** intentional 종료도 알림 받을지 여부 (기본 false) */
    private boolean notifyIntentional;

    private boolean enabled;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
