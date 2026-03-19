package com.lite_k8s.service;

import com.lite_k8s.config.SelfHealingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerLabelReaderTest {

    private ContainerLabelReader labelReader;

    @BeforeEach
    void setUp() {
        labelReader = new ContainerLabelReader();
    }

    @Test
    @DisplayName("라벨에서 self-healing.enabled=true를 읽을 수 있다")
    void shouldReadEnabledFromLabels() {
        Map<String, String> labels = Map.of("self-healing.enabled", "true");

        Optional<SelfHealingProperties.Rule> rule = labelReader.readHealingConfig(labels);

        assertThat(rule).isPresent();
    }

    @Test
    @DisplayName("라벨에서 self-healing.max-restarts 값을 읽을 수 있다")
    void shouldReadMaxRestartsFromLabels() {
        Map<String, String> labels = Map.of(
                "self-healing.enabled", "true",
                "self-healing.max-restarts", "5"
        );

        Optional<SelfHealingProperties.Rule> rule = labelReader.readHealingConfig(labels);

        assertThat(rule).isPresent();
        assertThat(rule.get().getMaxRestarts()).isEqualTo(5);
    }

    @Test
    @DisplayName("라벨이 없으면 empty를 반환한다")
    void shouldReturnEmptyWhenNoLabels() {
        Map<String, String> labels = new HashMap<>();

        Optional<SelfHealingProperties.Rule> rule = labelReader.readHealingConfig(labels);

        assertThat(rule).isEmpty();
    }

    @Test
    @DisplayName("self-healing.enabled가 없으면 empty를 반환한다")
    void shouldReturnEmptyWhenEnabledNotPresent() {
        Map<String, String> labels = Map.of("other-label", "value");

        Optional<SelfHealingProperties.Rule> rule = labelReader.readHealingConfig(labels);

        assertThat(rule).isEmpty();
    }

    @Test
    @DisplayName("self-healing.enabled=false면 empty를 반환한다")
    void shouldReturnEmptyWhenEnabledIsFalse() {
        Map<String, String> labels = Map.of("self-healing.enabled", "false");

        Optional<SelfHealingProperties.Rule> rule = labelReader.readHealingConfig(labels);

        assertThat(rule).isEmpty();
    }

    @Test
    @DisplayName("max-restarts가 없으면 기본값 3을 사용한다")
    void shouldUseDefaultMaxRestarts() {
        Map<String, String> labels = Map.of("self-healing.enabled", "true");

        Optional<SelfHealingProperties.Rule> rule = labelReader.readHealingConfig(labels);

        assertThat(rule).isPresent();
        assertThat(rule.get().getMaxRestarts()).isEqualTo(3);
    }

    @Test
    @DisplayName("labels가 null이면 empty를 반환한다")
    void shouldReturnEmptyWhenLabelsIsNull() {
        Optional<SelfHealingProperties.Rule> rule = labelReader.readHealingConfig(null);

        assertThat(rule).isEmpty();
    }
}
