package com.lite_k8s.compose;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.util.*;

public class ComposeParser {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @SuppressWarnings("unchecked")
    public static List<ParsedService> parse(String yaml) {
        try {
            Map<String, Object> root = YAML_MAPPER.readValue(yaml, Map.class);
            Map<String, Object> services = (Map<String, Object>) root.get("services");
            if (services == null) return List.of();

            List<ParsedService> result = new ArrayList<>();
            for (Map.Entry<String, Object> entry : services.entrySet()) {
                String serviceName = entry.getKey();
                Map<String, Object> config = (Map<String, Object>) entry.getValue();
                result.add(parseService(serviceName, config));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Compose YAML 파싱 실패", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static ParsedService parseService(String serviceName, Map<String, Object> config) {
        return ParsedService.builder()
                .serviceName(serviceName)
                .image(getString(config, "image"))
                .containerName(getString(config, "container_name", serviceName))
                .ports(getStringList(config, "ports"))
                .volumes(getStringList(config, "volumes"))
                .environment(parseEnvironment(config.get("environment")))
                .networks(getStringList(config, "networks"))
                .restartPolicy(getString(config, "restart"))
                .labels(parseLabels(config.get("labels")))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> parseEnvironment(Object env) {
        if (env == null) return Map.of();
        if (env instanceof Map<?, ?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(k.toString(), v != null ? v.toString() : ""));
            return result;
        }
        if (env instanceof List<?> list) {
            Map<String, String> result = new LinkedHashMap<>();
            for (Object item : list) {
                String s = item.toString();
                int idx = s.indexOf('=');
                if (idx > 0) result.put(s.substring(0, idx), s.substring(idx + 1));
                else result.put(s, "");
            }
            return result;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> parseLabels(Object labels) {
        if (labels == null) return Map.of();
        if (labels instanceof Map<?, ?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(k.toString(), v != null ? v.toString() : ""));
            return result;
        }
        return Map.of();
    }

    private static String getString(Map<String, Object> config, String key) {
        return getString(config, key, null);
    }

    private static String getString(Map<String, Object> config, String key, String defaultValue) {
        Object val = config.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getStringList(Map<String, Object> config, String key) {
        Object val = config.get(key);
        if (val == null) return List.of();
        if (val instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }
}
