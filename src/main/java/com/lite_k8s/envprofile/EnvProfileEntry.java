package com.lite_k8s.envprofile;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "env_profile_entries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvProfileEntry {

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @Column(name = "profile_id", nullable = false)
    private String profileId;

    @Column(name = "entry_key", nullable = false)
    private String key;

    @Column(name = "entry_value", columnDefinition = "TEXT")
    private String value;

    @Builder.Default
    private boolean secret = false;

    @PrePersist
    public void validate() {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("key는 필수입니다");
        }
    }
}
