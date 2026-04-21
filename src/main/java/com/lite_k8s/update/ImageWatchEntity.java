package com.lite_k8s.update;

import com.lite_k8s.envprofile.ImageRegistry;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    /** 이미지 레지스트리 참조 (FK) */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "image_registry_id")
    private ImageRegistry imageRegistry;

    /** 감시할 이미지 — imageRegistry가 있으면 거기서 가져옴 */
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

    /** 대상 노드 이름 목록 (빈 리스트면 전체 노드) */
    @Convert(converter = StringListConverter.class)
    @Column(name = "node_names", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> nodeNames = new ArrayList<>();

    /** 와치별 폴링 주기 (초, 기본 300) */
    @Column(name = "poll_interval_seconds")
    @Builder.Default
    private Integer pollIntervalSeconds = 300;

    /** 와치 모드: POLLING(자동 감시) / TRIGGER(수동 배포만) */
    @Column(name = "mode")
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private WatchMode mode = WatchMode.POLLING;

    /** GHCR 인증 토큰 — imageRegistry가 있으면 거기서 가져옴 */
    private String ghcrToken;

    /** 실제 사용할 토큰 반환: 와치 토큰 > 레지스트리 토큰 */
    public String getEffectiveGhcrToken() {
        if (ghcrToken != null && !ghcrToken.isBlank()) return ghcrToken;
        if (imageRegistry != null && imageRegistry.getGhcrToken() != null && !imageRegistry.getGhcrToken().isBlank()) {
            return imageRegistry.getGhcrToken();
        }
        return null;
    }

    /** 실제 사용할 이미지 경로 반환 */
    public String getEffectiveImage() {
        if (imageRegistry != null && imageRegistry.getImage() != null) return imageRegistry.getImage();
        return image;
    }

    public enum WatchMode { POLLING, TRIGGER }

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
