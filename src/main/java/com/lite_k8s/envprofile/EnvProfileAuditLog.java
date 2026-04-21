package com.lite_k8s.envprofile;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "env_profile_audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvProfileAuditLog {

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @Column(name = "profile_id")
    private String profileId;

    @Column(name = "profile_name")
    private String profileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    private String actor;

    /** 변경된 엔트리 key 목록 (쉼표 구분, value는 절대 기록 안 함) */
    @Column(name = "changed_keys")
    private String changedKeys;

    /** 변경 전 값의 SHA-256 해시 */
    @Column(name = "before_hash")
    private String beforeHash;

    /** 변경 후 값의 SHA-256 해시 */
    @Column(name = "after_hash")
    private String afterHash;

    /** action=REFERENCED일 때 어떤 컨테이너에 사용됐는지 */
    @Column(name = "referenced_container_name")
    private String referencedContainerName;

    public enum Action {
        CREATED, UPDATED, DELETED,
        ENTRY_ADDED, ENTRY_UPDATED, ENTRY_DELETED,
        REFERENCED
    }
}
