package com.gopair.websocketservice.config;

import com.gopair.websocketservice.handler.GlobalWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @PostConstruct
    public void init() {
        log.info("[WebSocket服务] WebSocket配置初始化完成");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 新架构：RESTful风格的WebSocket端点
        
        // 全局连接端点
        registry.addHandler(globalWebSocketHandler, "/api/ws/connect")
                .setAllowedOrigins("*");
        
        // 房间专用端点
        registry.addHandler(globalWebSocketHandler, "/api/ws/room/*")
                .setAllowedOrigins("*");
                
        // 语音专用端点  
        registry.addHandler(globalWebSocketHandler, "/api/ws/voice/*")
                .setAllowedOrigins("*");
                
        log.info("[WebSocket服务] 已注册新架构WebSocket端点: /api/ws/connect, /api/ws/room/*, /api/ws/voice/*");
    }
} 