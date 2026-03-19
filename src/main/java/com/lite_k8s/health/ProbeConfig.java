package com.lite_k8s.health;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProbeConfig {

    /** 프로브 타입 */
    @Builder.Default
    private ProbeType type = ProbeType.HTTP;

    /** HTTP/TCP 포트 */
    @Builder.Default
    private int port = 80;

    /** HTTP probe 경로 */
    @Builder.Default
    private String path = "/";

    /** EXEC probe 명령 */
    @Builder.Default
    private String[] command = new String[]{};

    /** 컨테이너 시작 후 첫 probe 전 대기 시간 (초) */
    @Builder.Default
    private int initialDelaySeconds = 10;

    /** probe 실행 주기 (초) */
    @Builder.Default
    private int periodSeconds = 15;

    /** 이 횟수만큼 연속 실패 시 unhealthy로 판정 */
    @Builder.Default
    private int failureThreshold = 3;

    /** 이 횟수만큼 연속 성공 시 healthy로 복구 판정 */
    @Builder.Default
    private int successThreshold = 1;

    /** HTTP probe 타임아웃 (초) */
    @Builder.Default
    private int timeoutSeconds = 5;
}
