package com.lite_k8s.compose;

import com.lite_k8s.update.StringMapConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;

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

    /** 노드별 Env Profile 매핑 — {"nodeName": "profileId"} */
    @Convert(converter = StringMapConverter.class)
    @Column(name = "node_env_mappings", columnDefinition = "TEXT")
    @Builder.Default
    private Map<String, String> nodeEnvMappings = new LinkedHashMap<>();

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

    /** 편의 메서드: 매핑된 노드 이름 목록 */
    public List<String> getNodeNames() {
        if (nodeEnvMappings == null || nodeEnvMappings.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(nodeEnvMappings.keySet());
    }

    /** 편의 메서드: 첫 번째 매핑의 profileId (하위호환) */
    public String getEnvProfileId() {
        if (nodeEnvMappings == null || nodeEnvMappings.isEmpty()) return null;
        return nodeEnvMappings.values().iterator().next();
    }

    public enum Status {
        DRAFT, DEPLOYED, STOPPED
    }
}
