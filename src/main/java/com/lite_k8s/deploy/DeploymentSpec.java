package com.lite_k8s.deploy;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DeploymentSpec {

    /** 서비스 이름 */
    private final String serviceName;

    /** 배포할 새 이미지 */
    private final String newImage;

    /** 현재 실행 중인 컨테이너 목록 */
    private final List<RunningContainer> targets;

    /** RollingUpdate: 동시에 교체할 최대 수 (기본 1) */
    @Builder.Default
    private final int maxUnavailable = 1;

    /** Canary: 새 버전으로 전환할 비율 % (기본 20) */
    @Builder.Default
    private final int canaryWeight = 20;

    /** Blue-Green: Green 안정성 대기 시간 (ms, 기본 3초) */
    @Builder.Default
    private final long blueGreenWaitMs = 3000;

    @Getter
    @Builder
    public static class RunningContainer {
        private final String id;
        private final String name;
        private final String currentImage;
    }
}
