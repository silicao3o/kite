package com.lite_k8s.compose;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.util.*;

public class ComposeParser {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    /**
     * 모든 서비스를 파싱 (activeProfiles 필터 없음 — 기존 동작 호환)
     */
    @SuppressWarnings("unchecked")
    public static List<ParsedService> parse(String yaml) {
        return parse(yaml, null);
    }

    /**
     * activeProfiles로 서비스를 필터링하여 파싱.
     * - null: 모든 서비스 반환 (기존 동작)
     * - 빈 리스트: profiles가 없는 서비스만 반환
     * - ["with-nginx"]: profiles가 없는 서비스 + profiles에 "with-nginx"가 포함된 서비스
     */
    @SuppressWarnings("unchecked")
    public static List<ParsedService> parse(String yaml, List<String> activeProfiles) {
        try {
            Map<String, Object> root = YAML_MAPPER.readValue(yaml, Map.class);
            Map<String, Object> services = (Map<String, Object>) root.get("services");
            if (services == null) return List.of();

            List<ParsedService> result = new ArrayList<>();
            for (Map.Entry<String, Object> entry : services.entrySet()) {
                String serviceName = entry.getKey();
                Map<String, Object> config = (Map<String, Object>) entry.getValue();
                ParsedService parsed = parseService(serviceName, config);

                // activeProfiles 필터링
                if (activeProfiles != null) {
                    List<String> svcProfiles = parsed.getProfiles();
                    if (svcProfiles != null && !svcProfiles.isEmpty()) {
                        // profiles가 정의된 서비스는 activeProfiles와 교집합이 있어야 포함
                        boolean match = svcProfiles.stream().anyMatch(activeProfiles::contains);
                        if (!match) continue;
                    }
                    // profiles가 없는 서비스는 항상 포함
                }

                result.add(parsed);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Compose YAML 파싱 실패", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static ParsedService parseService(String serviceName, Map<String, Object> config) {
        // deploy.resources.limits 파싱
        String memoryLimit = null;
        String cpuLimit = null;
        Object deploy = config.get("deploy");
        if (deploy instanceof Map<?, ?> deployMap) {
            Object resources = ((Map<?, ?>) deployMap).get("resources");
            if (resources instanceof Map<?, ?> resMap) {
                Object limits = ((Map<?, ?>) resMap).get("limits");
                if (limits instanceof Map<?, ?> limitsMap) {
                    memoryLimit = limitsMap.get("memory") != null ? limitsMap.get("memory").toString() : null;
                    cpuLimit = limitsMap.get("cpus") != null ? limitsMap.get("cpus").toString() : null;
                }
            }
        }

        return ParsedService.builder()
                .serviceName(serviceName)
                .image(getString(config, "image"))
                .containerName(getString(config, "container_name", serviceName))
                .ports(getStringList(config, "ports"))
                .volumes(getStringList(config, "volumes"))
                .environment(parseEnvironment(config.get("environment")))
                .networks(getStringList(config, "networks"))
                .profiles(getStringList(config, "profiles"))
                .restartPolicy(getString(config, "restart"))
                .labels(parseLabels(config.get("labels")))
                .extraHosts(getStringList(config, "extra_hosts"))
                .dependsOn(getStringList(config, "depends_on"))
                .memoryLimit(memoryLimit)
                .cpuLimit(cpuLimit)
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
