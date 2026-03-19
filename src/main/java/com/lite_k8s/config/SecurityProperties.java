package com.lite_k8s.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docker.monitor.security")
public class SecurityProperties {

    // JWT 인증 활성화 (기본: 비활성화)
    private boolean enabled = false;

    // JWT 설정
    private Jwt jwt = new Jwt();

    // 사용자 설정
    private User user = new User();

    @Getter
    @Setter
    public static class Jwt {
        // JWT 서명 비밀키 (최소 32자 이상 권장)
        private String secret = "docker-monitor-default-secret-key-change-in-production";
        // 토큰 만료 시간 (초, 기본 24시간)
        private long expirationSeconds = 86400;
    }

    @Getter
    @Setter
    public static class User {
        // 기본 사용자명
        private String username = "admin";
        // 기본 비밀번호 (BCrypt 해시 또는 평문)
        private String password = "admin";
    }
}
