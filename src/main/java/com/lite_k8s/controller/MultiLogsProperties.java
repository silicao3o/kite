package com.lite_k8s.controller;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docker.monitor.multi-logs")
public class MultiLogsProperties {

    private List<Preset> presets = new ArrayList<>();

    @Getter
    @Setter
    public static class Preset {
        private String name;
        private int panels = 2;
        /** 컨테이너 이름 목록 (패널 순서대로, 최대 4개) */
        private List<String> containers = new ArrayList<>();
    }
}
