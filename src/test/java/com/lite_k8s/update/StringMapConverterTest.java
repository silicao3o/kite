package com.lite_k8s.update;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StringMapConverterTest {

    private final StringMapConverter converter = new StringMapConverter();

    @Test
    void convertToDatabaseColumn_returnsJsonString() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("node-01", "profile-a");
        map.put("node-02", "profile-b");

        String json = converter.convertToDatabaseColumn(map);

        assertThat(json).contains("\"node-01\"");
        assertThat(json).contains("\"profile-a\"");
        assertThat(json).contains("\"node-02\"");
        assertThat(json).contains("\"profile-b\"");
    }

    @Test
    void convertToDatabaseColumn_nullReturnsEmptyJson() {
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("{}");
    }

    @Test
    void convertToDatabaseColumn_emptyMapReturnsEmptyJson() {
        assertThat(converter.convertToDatabaseColumn(Map.of())).isEqualTo("{}");
    }

    @Test
    void convertToEntityAttribute_parsesJsonToMap() {
        String json = "{\"node-01\":\"profile-a\",\"node-02\":\"profile-b\"}";

        Map<String, String> result = converter.convertToEntityAttribute(json);

        assertThat(result).hasSize(2);
        assertThat(result.get("node-01")).isEqualTo("profile-a");
        assertThat(result.get("node-02")).isEqualTo("profile-b");
    }

    @Test
    void convertToEntityAttribute_nullReturnsEmptyMap() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }

    @Test
    void convertToEntityAttribute_blankReturnsEmptyMap() {
        assertThat(converter.convertToEntityAttribute("  ")).isEmpty();
    }

    @Test
    void convertToEntityAttribute_invalidJsonReturnsEmptyMap() {
        assertThat(converter.convertToEntityAttribute("not-json")).isEmpty();
    }

    @Test
    void roundTrip() {
        Map<String, String> original = Map.of("local", "env-prod", "staging", "env-staging");

        String json = converter.convertToDatabaseColumn(original);
        Map<String, String> restored = converter.convertToEntityAttribute(json);

        assertThat(restored).isEqualTo(original);
    }
}
