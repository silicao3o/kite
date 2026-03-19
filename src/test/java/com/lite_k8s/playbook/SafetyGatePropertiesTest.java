package com.lite_k8s.playbook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SafetyGatePropertiesTest {

    @Test
    @DisplayName("서비스 중요도 규칙을 설정할 수 있다")
    void shouldConfigureServiceCriticalityRules() {
        // given
        SafetyGateProperties properties = new SafetyGateProperties();
        SafetyGateProperties.ServiceCriticalityConfig rule1 = new SafetyGateProperties.ServiceCriticalityConfig();
        rule1.setNamePattern("db-*");
        rule1.setCriticality(ServiceCriticality.CRITICAL);

        SafetyGateProperties.ServiceCriticalityConfig rule2 = new SafetyGateProperties.ServiceCriticalityConfig();
        rule2.setNamePattern("api-*");
        rule2.setCriticality(ServiceCriticality.HIGH);

        properties.setServiceCriticality(List.of(rule1, rule2));

        // then
        assertThat(properties.getServiceCriticality()).hasSize(2);
        assertThat(properties.getServiceCriticality().get(0).getNamePattern()).isEqualTo("db-*");
        assertThat(properties.getServiceCriticality().get(0).getCriticality()).isEqualTo(ServiceCriticality.CRITICAL);
    }

    @Test
    @DisplayName("서비스 중요도 규칙을 ServiceCriticalityRule로 변환할 수 있다")
    void shouldConvertToServiceCriticalityRules() {
        // given
        SafetyGateProperties properties = new SafetyGateProperties();
        SafetyGateProperties.ServiceCriticalityConfig config = new SafetyGateProperties.ServiceCriticalityConfig();
        config.setNamePattern("db-*");
        config.setCriticality(ServiceCriticality.CRITICAL);
        properties.setServiceCriticality(List.of(config));

        // when
        List<ServiceCriticalityRule> rules = properties.toServiceCriticalityRules();

        // then
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getNamePattern()).isEqualTo("db-*");
        assertThat(rules.get(0).getCriticality()).isEqualTo(ServiceCriticality.CRITICAL);
    }

    @Test
    @DisplayName("기본 중요도를 설정할 수 있다")
    void shouldConfigureDefaultCriticality() {
        // given
        SafetyGateProperties properties = new SafetyGateProperties();
        properties.setDefaultCriticality(ServiceCriticality.HIGH);

        // then
        assertThat(properties.getDefaultCriticality()).isEqualTo(ServiceCriticality.HIGH);
    }
}
