package com.lite_k8s.compose;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceDefinitionNodeEnvMappingsTest {

    @Test
    @DisplayName("nodeEnvMappings로 노드별 env profile을 매핑할 수 있다")
    void nodeEnvMappingsField() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("node-01", "profile-a");
        mappings.put("node-02", "profile-b");

        ServiceDefinition def = ServiceDefinition.builder()
                .name("test-svc")
                .composeYaml("services:\n  app:\n    image: nginx")
                .nodeEnvMappings(mappings)
                .build();

        assertThat(def.getNodeEnvMappings()).hasSize(2);
        assertThat(def.getNodeEnvMappings().get("node-01")).isEqualTo("profile-a");
        assertThat(def.getNodeEnvMappings().get("node-02")).isEqualTo("profile-b");
    }

    @Test
    @DisplayName("nodeEnvMappings 기본값은 빈 맵이다")
    void defaultNodeEnvMappingsIsEmpty() {
        ServiceDefinition def = new ServiceDefinition();
        assertThat(def.getNodeEnvMappings()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("getNodeNames()는 nodeEnvMappings의 키 목록을 반환한다")
    void getNodeNamesFromMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("node-01", "profile-a");
        mappings.put("node-02", "profile-b");

        ServiceDefinition def = ServiceDefinition.builder()
                .name("test")
                .composeYaml("services:\n  x:\n    image: nginx")
                .nodeEnvMappings(mappings)
                .build();

        assertThat(def.getNodeNames()).containsExactlyInAnyOrder("node-01", "node-02");
    }

    @Test
    @DisplayName("getNodeNames()는 매핑이 비어있으면 빈 리스트를 반환한다")
    void getNodeNamesEmptyWhenNoMappings() {
        ServiceDefinition def = new ServiceDefinition();
        assertThat(def.getNodeNames()).isEmpty();
    }

    @Test
    @DisplayName("getEnvProfileId()는 첫 번째 매핑의 profileId를 반환한다")
    void getEnvProfileIdFromFirstMapping() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("node-01", "profile-a");

        ServiceDefinition def = ServiceDefinition.builder()
                .name("test")
                .composeYaml("services:\n  x:\n    image: nginx")
                .nodeEnvMappings(mappings)
                .build();

        assertThat(def.getEnvProfileId()).isEqualTo("profile-a");
    }

    @Test
    @DisplayName("getEnvProfileId()는 매핑이 비어있으면 null을 반환한다")
    void getEnvProfileIdNullWhenEmpty() {
        ServiceDefinition def = new ServiceDefinition();
        assertThat(def.getEnvProfileId()).isNull();
    }
}
