package com.lite_k8s.compose;

import com.lite_k8s.update.StringListConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "service_definitions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDefinition {

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "compose_yaml", columnDefinition = "TEXT", nullable = false)
    private String composeYaml;

    /** 연결된 Env Profile ID (nullable) */
    @Column(name = "env_profile_id")
    private String envProfileId;

    /** 배포 대상 노드 */
    @Convert(converter = StringListConverter.class)
    @Column(name = "node_names", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> nodeNames = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void validate() {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("name은 필수입니다");
        }
        if (composeYaml == null || composeYaml.isBlank()) {
            throw new IllegalStateException("composeYaml은 필수입니다");
        }
    }

    public enum Status {
        DRAFT, DEPLOYED, STOPPED
    }
}
