package com.lite_k8s.playbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceCriticalityResolverTest {

    private ServiceCriticalityResolver resolver;

    @BeforeEach
    void setUp() {
        // 서비스 중요도 설정
        List<ServiceCriticalityRule> rules = List.of(
                new ServiceCriticalityRule("db-*", ServiceCriticality.CRITICAL),
                new ServiceCriticalityRule("api-*", ServiceCriticality.HIGH),
                new ServiceCriticalityRule("worker-*", ServiceCriticality.NORMAL),
                new ServiceCriticalityRule("test-*", ServiceCriticality.LOW)
        );
        resolver = new ServiceCriticalityResolver(rules);
    }

    @Test
    @DisplayName("컨테이너 이름 패턴으로 서비스 중요도를 결정한다")
    void shouldResolveCriticalityByNamePattern() {
        // when & then
        assertThat(resolver.resolve("db-postgres")).isEqualTo(ServiceCriticality.CRITICAL);
        assertThat(resolver.resolve("api-gateway")).isEqualTo(ServiceCriticality.HIGH);
        assertThat(resolver.resolve("worker-email")).isEqualTo(ServiceCriticality.NORMAL);
        assertThat(resolver.resolve("test-unit")).isEqualTo(ServiceCriticality.LOW);
    }

    @Test
    @DisplayName("매칭되는 패턴이 없으면 기본값 NORMAL을 반환한다")
    void shouldReturnDefaultWhenNoPatternMatches() {
        // when
        ServiceCriticality criticality = resolver.resolve("unknown-service");

        // then
        assertThat(criticality).isEqualTo(ServiceCriticality.NORMAL);
    }

    @Test
    @DisplayName("라벨에서 서비스 중요도를 읽는다")
    void shouldResolveCriticalityFromLabels() {
        // given
        Map<String, String> labels = Map.of(
                "service.criticality", "CRITICAL"
        );

        // when
        ServiceCriticality criticality = resolver.resolveFromLabels(labels);

        // then
        assertThat(criticality).isEqualTo(ServiceCriticality.CRITICAL);
    }

    @Test
    @DisplayName("라벨 설정이 패턴 매칭보다 우선한다")
    void shouldPreferLabelsOverPatterns() {
        // given
        String containerName = "test-unit";  // 패턴 매칭시 LOW
        Map<String, String> labels = Map.of(
                "service.criticality", "HIGH"
        );

        // when
        ServiceCriticality criticality = resolver.resolve(containerName, labels);

        // then
        assertThat(criticality).isEqualTo(ServiceCriticality.HIGH);
    }

    @Test
    @DisplayName("라벨이 없으면 패턴 매칭으로 결정한다")
    void shouldFallbackToPatternWhenNoLabel() {
        // given
        String containerName = "db-mysql";
        Map<String, String> labels = Map.of();  // 빈 라벨

        // when
        ServiceCriticality criticality = resolver.resolve(containerName, labels);

        // then
        assertThat(criticality).isEqualTo(ServiceCriticality.CRITICAL);
    }
}
