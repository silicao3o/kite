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
 *   poll-interval-seconds: 300
 *   ghcr-token: ${GHCR_TOKEN:}
 *   watches:
 *     - image: ghcr.io/myorg/myapp
 *       tag: latest
 *       container-pattern: "myapp-.*"
 *       max-unavailable: 1
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docker.monitor.image-watch")
public class ImageWatchProperties {

    /** 이미지 자동 업데이트 활성화 */
    private boolean enabled = false;

    /** 폴링 주기 (초, 기본 5분) */
    private int pollIntervalSeconds = 300;

    /** GHCR 인증 토큰 (private 이미지용, public은 불필요) */
    private String ghcrToken = "";

    /** 감시할 이미지 목록 */
    private List<ImageWatch> watches = new ArrayList<>();

    @Getter
    @Setter
    public static class ImageWatch {
        /** 감시할 이미지 (ghcr.io/owner/repo 형태) */
        private String image;

        /** 감시할 태그 (기본 latest) */
        private String tag = "latest";

        /** 업데이트 대상 컨테이너 이름 패턴 (정규식) */
        private String containerPattern;

        /** 동시에 업데이트할 수 있는 최대 컨테이너 수 (기본 1 = 순차 업데이트) */
        private int maxUnavailable = 1;
    }
}
