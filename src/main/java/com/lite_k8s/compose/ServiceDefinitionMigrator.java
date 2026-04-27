package com.lite_k8s.compose;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 기존 env_profile_id + node_names → node_env_mappings 마이그레이션.
 * ddl-auto:update 환경에서 기존 컬럼이 남아있을 때 자동 변환.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceDefinitionMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void run(ApplicationArguments args) {
        try {
            // 기존 컬럼 존재 여부 확인
            var columns = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'SERVICE_DEFINITIONS' AND COLUMN_NAME = 'ENV_PROFILE_ID'");
            if (columns.isEmpty()) return; // 이미 마이그레이션 완료

            log.info("서비스 정의 마이그레이션 시작: env_profile_id + node_names → node_env_mappings");

            var rows = jdbc.queryForList(
                "SELECT id, env_profile_id, node_names, node_env_mappings FROM service_definitions WHERE node_env_mappings IS NULL OR node_env_mappings = '{}'");

            for (var row : rows) {
                String id = (String) row.get("id");
                String profileId = (String) row.get("env_profile_id");
                String nodeNamesJson = (String) row.get("node_names");

                if (profileId == null && (nodeNamesJson == null || "[]".equals(nodeNamesJson))) continue;

                Map<String, String> mappings = new LinkedHashMap<>();
                List<String> nodeNames = List.of();
                if (nodeNamesJson != null && !nodeNamesJson.isBlank() && !"[]".equals(nodeNamesJson)) {
                    nodeNames = MAPPER.readValue(nodeNamesJson, new TypeReference<>() {});
                }

                if (nodeNames.isEmpty()) {
                    if (profileId != null) mappings.put("", profileId);
                } else {
                    for (String node : nodeNames) {
                        mappings.put(node, profileId);
                    }
                }

                String mappingsJson = MAPPER.writeValueAsString(mappings);
                jdbc.update("UPDATE service_definitions SET node_env_mappings = ? WHERE id = ?", mappingsJson, id);
                log.info("마이그레이션 완료: {} → {}", id, mappingsJson);
            }
        } catch (Exception e) {
            log.warn("서비스 정의 마이그레이션 스킵 (컬럼 미존재 또는 오류): {}", e.getMessage());
        }
    }
}
