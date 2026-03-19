package com.lite_k8s.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class ConfigurationValidatorTest {

    private MonitorProperties monitorProperties;
    private ConfigurationValidator validator;

    @BeforeEach
    void setUp() {
        monitorProperties = new MonitorProperties();
        monitorProperties.getNotification().getEmail().setTo("test@example.com");
        validator = new ConfigurationValidator(monitorProperties);
    }

    @Test
    @DisplayName("모든 필수 설정이 있으면 검증 통과")
    void validate_WithAllRequiredSettings_ShouldPass() {
        // given
        ReflectionTestUtils.setField(validator, "dockerHost", "unix:///var/run/docker.sock");
        ReflectionTestUtils.setField(validator, "mailHost", "smtp.gmail.com");
        ReflectionTestUtils.setField(validator, "mailUsername", "test@gmail.com");

        // when & then
        assertThatCode(() -> validator.validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Docker Host 누락 시 예외 발생")
    void validate_WithoutDockerHost_ShouldThrowException() {
        // given
        ReflectionTestUtils.setField(validator, "dockerHost", "");
        ReflectionTestUtils.setField(validator, "mailHost", "smtp.gmail.com");
        ReflectionTestUtils.setField(validator, "mailUsername", "test@gmail.com");

        // when & then
        assertThatThrownBy(() -> validator.validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("docker.host");
    }

    @Test
    @DisplayName("Mail Host 누락 시 예외 발생")
    void validate_WithoutMailHost_ShouldThrowException() {
        // given
        ReflectionTestUtils.setField(validator, "dockerHost", "unix:///var/run/docker.sock");
        ReflectionTestUtils.setField(validator, "mailHost", "");
        ReflectionTestUtils.setField(validator, "mailUsername", "test@gmail.com");

        // when & then
        assertThatThrownBy(() -> validator.validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.host");
    }

    @Test
    @DisplayName("알림 이메일 누락 시 예외 발생")
    void validate_WithoutAlertEmail_ShouldThrowException() {
        // given
        ReflectionTestUtils.setField(validator, "dockerHost", "unix:///var/run/docker.sock");
        ReflectionTestUtils.setField(validator, "mailHost", "smtp.gmail.com");
        ReflectionTestUtils.setField(validator, "mailUsername", "test@gmail.com");
        monitorProperties.getNotification().getEmail().setTo(null);

        // when & then
        assertThatThrownBy(() -> validator.validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notification.email.to");
    }

    @Test
    @DisplayName("여러 설정 누락 시 모두 에러 메시지에 포함")
    void validate_WithMultipleMissingSettings_ShouldIncludeAllInError() {
        // given
        ReflectionTestUtils.setField(validator, "dockerHost", "");
        ReflectionTestUtils.setField(validator, "mailHost", "");
        ReflectionTestUtils.setField(validator, "mailUsername", "");
        monitorProperties.getNotification().getEmail().setTo(null);

        // when & then
        assertThatThrownBy(() -> validator.validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("docker.host")
                .hasMessageContaining("spring.mail.host")
                .hasMessageContaining("spring.mail.username")
                .hasMessageContaining("notification.email.to");
    }
}
