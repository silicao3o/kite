package com.lite_k8s.update;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "image_watches")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageWatchEntity {

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /** 감시할 이미지 (ghcr.io/owner/repo 형태) */
    @Column(nullable = false)
    private String image;

    /** 감시할 태그 (기본 latest) */
    @Builder.Default
    private String tag = "latest";

    /** 업데이트 대상 컨테이너 이름 패턴 (정규식) */
    private String containerPattern;

    /** 동시에 업데이트할 수 있는 최대 컨테이너 수 */
    @Builder.Default
    private int maxUnavailable = 1;

    /** GHCR 인증 토큰 (null이면 글로벌 설정 폴백) */
    private String ghcrToken;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void validate() {
        if (image == null || image.isBlank()) {
            throw new IllegalStateException("image는 필수입니다");
        }
    }
}
