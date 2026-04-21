package com.lite_k8s.envprofile;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "image_registry")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageRegistry {

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /** GHCR 이미지 경로 (예: ghcr.io/daquv-qv/quvi) */
    @Column(nullable = false, unique = true)
    private String image;

    /** 별칭 (예: engine-dev, quvi-operia) */
    private String alias;

    /** 설명 */
    private String description;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
