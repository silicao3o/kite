package com.lite_k8s.config;

import com.lite_k8s.websocket.ContainerStatusHandler;
import com.lite_k8s.websocket.LogStreamHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ContainerStatusHandler containerStatusHandler;
    private final LogStreamHandler logStreamHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(containerStatusHandler, "/ws/containers")
                .setAllowedOrigins("*");

        registry.addHandler(logStreamHandler, "/ws/logs")
                .setAllowedOrigins("*");
    }
}
