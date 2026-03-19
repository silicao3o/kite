package com.lite_k8s.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lite_k8s.service.DockerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 컨테이너 로그 실시간 스트리밍 WebSocket 핸들러
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogStreamHandler extends TextWebSocketHandler {

    private final DockerService dockerService;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();  // sessionId -> containerIds
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        subscriptions.put(sessionId, new CopyOnWriteArraySet<>());
        log.info("로그 스트리밍 WebSocket 연결: {}", sessionId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        subscriptions.remove(sessionId);
        log.info("로그 스트리밍 WebSocket 종료: {} ({})", sessionId, status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode json = objectMapper.readTree(message.getPayload());
            String action = json.has("action") ? json.get("action").asText() : "";
            String containerId = json.has("containerId") ? json.get("containerId").asText() : "";

            switch (action) {
                case "subscribe" -> subscribe(session, containerId);
                case "unsubscribe" -> unsubscribe(session, containerId);
                default -> log.warn("알 수 없는 액션: {}", action);
            }
        } catch (Exception e) {
            log.error("메시지 처리 실패: {}", e.getMessage());
            sendError(session, "Invalid message format");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("로그 스트리밍 WebSocket 오류: {}", session.getId(), exception);
        String sessionId = session.getId();
        sessions.remove(sessionId);
        subscriptions.remove(sessionId);
    }

    private void subscribe(WebSocketSession session, String containerId) {
        if (containerId == null || containerId.isEmpty()) {
            sendError(session, "containerId is required");
            return;
        }

        String sessionId = session.getId();
        Set<String> containerIds = subscriptions.get(sessionId);
        if (containerIds != null) {
            containerIds.add(containerId);
            log.info("로그 구독 시작: session={}, container={}", sessionId, containerId);

            // 구독 확인 메시지 전송
            sendMessage(session, new LogStreamMessage(
                    "subscribed",
                    containerId,
                    null,
                    LocalDateTime.now()
            ));
        }
    }

    private void unsubscribe(WebSocketSession session, String containerId) {
        String sessionId = session.getId();
        Set<String> containerIds = subscriptions.get(sessionId);
        if (containerIds != null) {
            containerIds.remove(containerId);
            log.info("로그 구독 해제: session={}, container={}", sessionId, containerId);

            sendMessage(session, new LogStreamMessage(
                    "unsubscribed",
                    containerId,
                    null,
                    LocalDateTime.now()
            ));
        }
    }

    /**
     * 특정 컨테이너의 로그를 구독 중인 모든 세션에 브로드캐스트
     */
    public void broadcastLog(String containerId, String logLine) {
        LogStreamMessage message = new LogStreamMessage(
                "log",
                containerId,
                logLine,
                LocalDateTime.now()
        );

        for (Map.Entry<String, Set<String>> entry : subscriptions.entrySet()) {
            String sessionId = entry.getKey();
            Set<String> containerIds = entry.getValue();

            if (containerIds.contains(containerId)) {
                WebSocketSession session = sessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    sendMessage(session, message);
                }
            }
        }
    }

    private void sendMessage(WebSocketSession session, LogStreamMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("메시지 전송 실패: {}", session.getId(), e);
        }
    }

    private void sendError(WebSocketSession session, String errorMessage) {
        sendMessage(session, new LogStreamMessage(
                "error",
                null,
                errorMessage,
                LocalDateTime.now()
        ));
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    public boolean isSubscribed(String sessionId, String containerId) {
        Set<String> containerIds = subscriptions.get(sessionId);
        return containerIds != null && containerIds.contains(containerId);
    }

    public record LogStreamMessage(
            String type,
            String containerId,
            String content,
            LocalDateTime timestamp
    ) {}
}
