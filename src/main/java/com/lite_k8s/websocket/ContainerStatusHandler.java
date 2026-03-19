package com.lite_k8s.websocket;

import com.lite_k8s.model.ContainerInfo;
import com.lite_k8s.model.ContainerMetrics;
import com.lite_k8s.service.MetricsScheduler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContainerStatusHandler extends TextWebSocketHandler {

    private final MetricsScheduler metricsScheduler;

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket 연결됨: {}", session.getId());

        // 연결 직후 현재 상태 전송
        sendCurrentStatus(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket 연결 종료: {} ({})", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 클라이언트로부터 refresh 요청을 받으면 현재 상태 전송
        String payload = message.getPayload();
        if ("refresh".equals(payload)) {
            sendCurrentStatus(session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket 오류: {}", session.getId(), exception);
        sessions.remove(session);
    }

    @Scheduled(fixedRateString = "${docker.monitor.metrics.collection-interval-seconds:15}000")
    public void broadcastStatus() {
        if (sessions.isEmpty()) {
            return;
        }

        try {
            List<ContainerInfo> containers = metricsScheduler.getCachedContainers();
            Map<String, ContainerMetrics> metrics = metricsScheduler.getAllCachedMetrics();

            applyMetrics(containers, metrics);

            ContainerStatusMessage statusMessage = new ContainerStatusMessage(
                    "update",
                    containers,
                    System.currentTimeMillis()
            );

            String json = objectMapper.writeValueAsString(statusMessage);
            TextMessage textMessage = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        log.warn("메시지 전송 실패: {}", session.getId());
                        sessions.remove(session);
                    }
                }
            }

            log.debug("WebSocket 브로드캐스트: {} 세션, {} 컨테이너", sessions.size(), containers.size());

        } catch (Exception e) {
            log.error("브로드캐스트 오류", e);
        }
    }

    private void sendCurrentStatus(WebSocketSession session) {
        try {
            List<ContainerInfo> containers = metricsScheduler.getCachedContainers();
            Map<String, ContainerMetrics> metrics = metricsScheduler.getAllCachedMetrics();

            applyMetrics(containers, metrics);

            ContainerStatusMessage statusMessage = new ContainerStatusMessage(
                    "initial",
                    containers,
                    System.currentTimeMillis()
            );

            String json = objectMapper.writeValueAsString(statusMessage);
            session.sendMessage(new TextMessage(json));

        } catch (Exception e) {
            log.error("현재 상태 전송 실패: {}", session.getId(), e);
        }
    }

    private void applyMetrics(List<ContainerInfo> containers, Map<String, ContainerMetrics> metrics) {
        containers.forEach(container -> {
            ContainerMetrics m = metrics.get(container.getId());
            if (m != null) {
                container.setCpuPercent(m.getCpuPercent());
                container.setMemoryUsage(m.getMemoryUsage());
                container.setMemoryLimit(m.getMemoryLimit());
                container.setMemoryPercent(m.getMemoryPercent());
                container.setNetworkRxBytes(m.getNetworkRxBytes());
                container.setNetworkTxBytes(m.getNetworkTxBytes());
            }
        });
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    public record ContainerStatusMessage(
            String type,
            List<ContainerInfo> containers,
            long timestamp
    ) {}
}
