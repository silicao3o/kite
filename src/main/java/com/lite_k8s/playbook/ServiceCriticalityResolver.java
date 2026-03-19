package com.lite_k8s.playbook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 서비스 중요도 결정기
 *
 * 컨테이너 이름 패턴 또는 라벨에서 서비스 중요도를 결정
 */
@Slf4j
@Component
public class ServiceCriticalityResolver {

    private static final String CRITICALITY_LABEL = "service.criticality";
    private static final ServiceCriticality DEFAULT_CRITICALITY = ServiceCriticality.NORMAL;

    private final List<ServiceCriticalityRule> rules;

    public ServiceCriticalityResolver(List<ServiceCriticalityRule> rules) {
        this.rules = rules != null ? rules : List.of();
    }

    /**
     * 컨테이너 이름으로 서비스 중요도 결정
     */
    public ServiceCriticality resolve(String containerName) {
        return resolveByPattern(containerName);
    }

    /**
     * 라벨에서 서비스 중요도 읽기
     */
    public ServiceCriticality resolveFromLabels(Map<String, String> labels) {
        if (labels == null || !labels.containsKey(CRITICALITY_LABEL)) {
            return null;
        }

        String value = labels.get(CRITICALITY_LABEL);
        try {
            return ServiceCriticality.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid service criticality label value: {}", value);
            return null;
        }
    }

    /**
     * 라벨 우선, 패턴 매칭 폴백으로 서비스 중요도 결정
     */
    public ServiceCriticality resolve(String containerName, Map<String, String> labels) {
        // 1. 라벨에서 먼저 확인
        ServiceCriticality fromLabels = resolveFromLabels(labels);
        if (fromLabels != null) {
            return fromLabels;
        }

        // 2. 패턴 매칭으로 폴백
        return resolveByPattern(containerName);
    }

    private ServiceCriticality resolveByPattern(String containerName) {
        for (ServiceCriticalityRule rule : rules) {
            if (matchesPattern(containerName, rule.getNamePattern())) {
                return rule.getCriticality();
            }
        }
        return DEFAULT_CRITICALITY;
    }

    private boolean matchesPattern(String containerName, String pattern) {
        if (pattern == null || containerName == null) {
            return false;
        }
        // 와일드카드 패턴을 정규식으로 변환
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        return Pattern.matches(regex, containerName);
    }
}
