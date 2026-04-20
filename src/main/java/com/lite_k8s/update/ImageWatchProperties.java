package com.lite_k8s.update;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * GHCR 이미지 자동 업데이트 설정
 *
 * 예시:
 * docker.monitor.image-watch:
 *   enabled: true
 *   ghcr-token: ${GHCR_TOKEN:}
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docker.monitor.image-watch")
public class ImageWatchProperties {

    /** 이미지 자동 업데이트 활성화 */
    private boolean enabled = false;

    /** GHCR 인증 토큰 (private 이미지용, public은 불필요) */
    private String ghcrToken = "";

    /** 감시할 이미지 목록 (레거시, DB 사용 시 비어있음) */
    private List<ImageWatch> watches = new ArrayList<>();

    @Getter
    @Setter
    public static class ImageWatch {
        private String image;
        private String tag = "latest";
        private String containerPattern;
        private int maxUnavailable = 1;
    }
}
