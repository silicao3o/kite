package com.lite_k8s.desired;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "desired_service_specs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesiredServiceSpecEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String image;

    @Builder.Default
    private int replicas = 1;

    private String containerNamePrefix;

    /** 노드 이름 (null = 로컬) */
    private String nodeName;

    /** 환경변수 JSON 배열: ["KEY=VAL", ...] */
    @Column(columnDefinition = "TEXT")
    private String envJson;

    /** 포트 매핑 JSON 배열: ["8080:8080", ...] */
    @Column(columnDefinition = "TEXT")
    private String portsJson;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** DB 엔티티를 StateReconciler가 사용하는 ServiceSpec으로 변환 */
    public DesiredStateProperties.ServiceSpec toServiceSpec() {
        DesiredStateProperties.ServiceSpec spec = new DesiredStateProperties.ServiceSpec();
        spec.setName(name);
        spec.setImage(image);
        spec.setReplicas(replicas);
        spec.setContainerNamePrefix(containerNamePrefix != null ? containerNamePrefix : name);
        spec.setNodeName(nodeName);
        spec.setEnv(parseJson(envJson));
        spec.setPorts(parseJson(portsJson));
        return spec;
    }

    private List<String> parseJson(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
