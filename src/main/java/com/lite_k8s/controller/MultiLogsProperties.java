package com.lite_k8s.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docker.monitor.multi-logs")
public class MultiLogsProperties {

    private static final Logger log = LoggerFactory.getLogger(MultiLogsProperties.class);

    /** application.yml 인라인 선언 */
    private List<Preset> presets = new ArrayList<>();

    /** 환경변수 MULTI_LOGS_PRESETS — JSON 배열 문자열로 presets 전체를 덮어씀 */
    private String presetsJson;

    @PostConstruct
    public void applyPresetsJson() {
        if (presetsJson == null || presetsJson.isBlank()) return;
        try {
            ObjectMapper mapper = new ObjectMapper();
            presets = mapper.readValue(presetsJson, new TypeReference<List<Preset>>() {});
        } catch (Exception e) {
            log.warn("MULTI_LOGS_PRESETS 파싱 실패 (yml 선언 유지): {}", e.getMessage());
        }
    }

    @Getter
    @Setter
    public static class Preset {
        private String name;
        private int panels = 2;
        /** 컨테이너 이름 목록 (패널 순서대로, 최대 4개) */
        private List<String> containers = new ArrayList<>();
    }
}
