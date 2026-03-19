package com.lite_k8s.websocket;

import com.lite_k8s.service.DockerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogStreamHandlerTest {

    @Mock
    private DockerService dockerService;

    @Mock
    private WebSocketSession session;

    private LogStreamHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LogStreamHandler(dockerService);
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);
    }

    @Test
    @DisplayName("WebSocket 연결 시 세션 추가")
    void shouldAddSessionOnConnect() throws Exception {
        // when
        handler.afterConnectionEstablished(session);

        // then
        assertThat(handler.getActiveSessionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("WebSocket 연결 종료 시 세션 제거")
    void shouldRemoveSessionOnClose() throws Exception {
        // given
        handler.afterConnectionEstablished(session);

        // when
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        // then
        assertThat(handler.getActiveSessionCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("subscribe 메시지로 로그 스트리밍 시작")
    void shouldStartStreamingOnSubscribe() throws Exception {
        // given
        handler.afterConnectionEstablished(session);
        String subscribeMessage = "{\"action\":\"subscribe\",\"containerId\":\"abc123\"}";

        // when
        handler.handleTextMessage(session, new TextMessage(subscribeMessage));

        // then
        assertThat(handler.isSubscribed("session-1", "abc123")).isTrue();
    }

    @Test
    @DisplayName("unsubscribe 메시지로 로그 스트리밍 중지")
    void shouldStopStreamingOnUnsubscribe() throws Exception {
        // given
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage("{\"action\":\"subscribe\",\"containerId\":\"abc123\"}"));

        // when
        handler.handleTextMessage(session, new TextMessage("{\"action\":\"unsubscribe\",\"containerId\":\"abc123\"}"));

        // then
        assertThat(handler.isSubscribed("session-1", "abc123")).isFalse();
    }

    @Test
    @DisplayName("여러 컨테이너 동시 구독 가능")
    void shouldAllowMultipleSubscriptions() throws Exception {
        // given
        handler.afterConnectionEstablished(session);

        // when
        handler.handleTextMessage(session, new TextMessage("{\"action\":\"subscribe\",\"containerId\":\"container-1\"}"));
        handler.handleTextMessage(session, new TextMessage("{\"action\":\"subscribe\",\"containerId\":\"container-2\"}"));

        // then
        assertThat(handler.isSubscribed("session-1", "container-1")).isTrue();
        assertThat(handler.isSubscribed("session-1", "container-2")).isTrue();
    }

    @Test
    @DisplayName("세션 종료 시 모든 구독 해제")
    void shouldUnsubscribeAllOnSessionClose() throws Exception {
        // given
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage("{\"action\":\"subscribe\",\"containerId\":\"container-1\"}"));

        // when
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        // then
        assertThat(handler.isSubscribed("session-1", "container-1")).isFalse();
    }
}
