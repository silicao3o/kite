package com.lite_k8s.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docker.monitor")
public class MonitorProperties {

    private int logTailLines = 50;
    private String serverName = "Production-Server-01";

    private Reconnect reconnect = new Reconnect();
    private Notification notification = new Notification();
    private Filter filter = new Filter();
    private Deduplication deduplication = new Deduplication();
    private Metrics metrics = new Metrics();
    private RestartLoop restartLoop = new RestartLoop();

    @Getter
    @Setter
    public static class Reconnect {
        // 초기 재연결 대기 시간 (밀리초)
        private long initialDelayMs = 5000;
        // 최대 재연결 대기 시간 (밀리초)
        private long maxDelayMs = 300000; // 5분
        // 백오프 배수
        private double multiplier = 2.0;
        // 최대 재시도 횟수 (0 = 무제한)
        private int maxRetries = 10;
    }

    @Getter
    @Setter
    public static class Notification {
        private Email email = new Email();
    }

    @Getter
    @Setter
    public static class Email {
        private String to;
        private String from;
    }

    @Getter
    @Setter
    public static class Filter {
        // 모니터링에서 제외할 컨테이너 이름 패턴 (정규식)
        private List<String> excludeNames = new ArrayList<>();
        // 모니터링에서 제외할 이미지 이름 패턴 (정규식)
        private List<String> excludeImages = new ArrayList<>();
        // 모니터링할 컨테이너 이름 패턴 (정규식, 비어있으면 모두 포함)
        private List<String> includeNames = new ArrayList<>();
        // 모니터링할 이미지 이름 패턴 (정규식, 비어있으면 모두 포함)
        private List<String> includeImages = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Deduplication {
        // 중복 방지 활성화
        private boolean enabled = true;
        // 중복 방지 시간 창 (초)
        private int windowSeconds = 60;
    }

    @Getter
    @Setter
    public static class Metrics {
        // 메트릭 수집 활성화
        private boolean enabled = true;
        // 메트릭 수집 주기 (초)
        private int collectionIntervalSeconds = 15;
        // 임계치 알림 활성화
        private boolean thresholdAlertEnabled = true;
        // CPU 임계치 (%)
        private double cpuThresholdPercent = 80.0;
        // 메모리 임계치 (%)
        private double memoryThresholdPercent = 90.0;
    }

    @Getter
    @Setter
    public static class RestartLoop {
        // 재시작 반복 알림 활성화
        private boolean enabled = true;
        // 임계 횟수 (이 횟수 이상 재시작하면 알림)
        private int thresholdCount = 3;
        // 시간 윈도우 (분)
        private int windowMinutes = 5;
    }
}
