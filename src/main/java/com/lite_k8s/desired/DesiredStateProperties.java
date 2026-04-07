package com.lite_k8s.desired;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Desired State 선언 설정
 *
 * 예시:
 * docker.monitor.desired-state:
 *   enabled: true
 *   reconcile-interval-seconds: 30
 *   services:
 *     - name: demo-api
 *       image: ghcr.io/myorg/demo-api:latest
 *       replicas: 2
 *       container-name-prefix: demo-api
 *       env:
 *         - "DB_HOST=postgres"
 *       ports:
 *         - "8081:8081"
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docker.monitor.desired-state")
public class DesiredStateProperties {

    /** Desired State 관리 활성화 */
    private boolean enabled = false;

    /** Reconcile 주기 (초) */
    private int reconcileIntervalSeconds = 30;

    /** 관리할 서비스 목록 */
    private List<ServiceSpec> services = new ArrayList<>();

    @Getter
    @Setter
    public static class ServiceSpec {
        /** 서비스 식별 이름 */
        private String name;

        /** 컨테이너 이미지 (tag 포함) */
        private String image;

        /** 유지할 컨테이너 수 */
        private int replicas = 1;

        /** 생성할 컨테이너 이름 prefix (prefix-1, prefix-2, ...) */
        private String containerNamePrefix;

        /** 환경변수 (KEY=VALUE 형태) */
        private List<String> env = new ArrayList<>();

        /** 포트 매핑 (hostPort:containerPort) */
        private List<String> ports = new ArrayList<>();

        /** 추가 레이블 */
        private Map<String, String> labels = new java.util.LinkedHashMap<>();

        /** 컨테이너를 실행할 노드 ID (null = 로컬) */
        private String nodeId;
    }
}
