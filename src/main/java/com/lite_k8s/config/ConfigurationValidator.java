package com.lite_k8s.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigurationValidator {

    private final MonitorProperties monitorProperties;

    @Value("${docker.host:}")
    private String dockerHost;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @PostConstruct
    public void validate() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 필수 설정 검증
        validateRequired(dockerHost, "docker.host", errors);
        validateRequired(mailHost, "spring.mail.host", errors);
        validateRequired(mailUsername, "spring.mail.username", errors);
        validateRequired(monitorProperties.getNotification().getEmail().getTo(),
                "docker.monitor.notification.email.to", errors);

        // 권장 설정 검증
        if (monitorProperties.getServerName() == null ||
            monitorProperties.getServerName().equals("Production-Server-01")) {
            warnings.add("docker.monitor.server-name이 기본값입니다. 서버 식별을 위해 변경을 권장합니다.");
        }

        // 경고 출력
        for (String warning : warnings) {
            log.warn("설정 경고: {}", warning);
        }

        // 에러가 있으면 예외 발생
        if (!errors.isEmpty()) {
            String errorMessage = String.join("\n", errors);
            log.error("필수 설정 누락:\n{}", errorMessage);
            throw new IllegalStateException("필수 설정이 누락되었습니다:\n" + errorMessage);
        }

        log.info("설정 검증 완료 - 모든 필수 설정이 올바르게 구성되었습니다.");
        logConfiguration();
    }

    private void validateRequired(String value, String propertyName, List<String> errors) {
        if (value == null || value.trim().isEmpty()) {
            errors.add("- " + propertyName + " 설정이 필요합니다.");
        }
    }

    private void logConfiguration() {
        log.info("=== Docker Monitor 설정 ===");
        log.info("Docker Host: {}", dockerHost);
        log.info("Server Name: {}", monitorProperties.getServerName());
        log.info("Log Tail Lines: {}", monitorProperties.getLogTailLines());
        log.info("Email To: {}", maskEmail(monitorProperties.getNotification().getEmail().getTo()));
        log.info("Reconnect Max Retries: {}", monitorProperties.getReconnect().getMaxRetries());
        log.info("Deduplication Enabled: {}", monitorProperties.getDeduplication().isEnabled());
        log.info("Deduplication Window: {}초", monitorProperties.getDeduplication().getWindowSeconds());

        MonitorProperties.Filter filter = monitorProperties.getFilter();
        if (!filter.getExcludeNames().isEmpty()) {
            log.info("Exclude Names: {}", filter.getExcludeNames());
        }
        if (!filter.getExcludeImages().isEmpty()) {
            log.info("Exclude Images: {}", filter.getExcludeImages());
        }
        log.info("===========================");
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        String local = parts[0];
        if (local.length() <= 2) {
            return email;
        }
        return local.substring(0, 2) + "***@" + parts[1];
    }
}
