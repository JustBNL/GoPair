package com.gopair.websocketservice.config;

import com.gopair.websocketservice.handler.GlobalWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import jakarta.annotation.PostConstruct;

/**
 * WebSocket配置类
 * 配置统一的WebSocket端点
 * 
 * @author gopair
 */
@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final GlobalWebSocketHandler globalWebSocketHandler;

    @Value("${gopair.websocket.allowed-origins:*}")
    private String allowedOrigins;

    @PostConstruct
    public void init() {
        log.info("[WebSocket服务] WebSocket配置初始化完成");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 新架构：RESTful风格的WebSocket端点
        
        String[] origins = parseAllowedOrigins(allowedOrigins);
        
        // 全局连接端点
        registry.addHandler(globalWebSocketHandler, "/api/ws/connect")
                .setAllowedOrigins(origins);
        
        // 房间专用端点
        registry.addHandler(globalWebSocketHandler, "/api/ws/room/*")
                .setAllowedOrigins(origins);
                
        // 语音专用端点  
        registry.addHandler(globalWebSocketHandler, "/api/ws/voice/*")
                .setAllowedOrigins(origins);
                
        log.info("[WebSocket服务] 已注册新架构WebSocket端点: /api/ws/connect, /api/ws/room/*, /api/ws/voice/*，origins={}", (Object) origins);
    }

    private String[] parseAllowedOrigins(String origins) {
        if (origins == null || origins.isBlank()) {
            return new String[]{"*"};
        }
        return java.util.Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }
} 