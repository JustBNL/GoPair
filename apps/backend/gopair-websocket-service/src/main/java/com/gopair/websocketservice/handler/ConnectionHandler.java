package com.gopair.websocketservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.websocketservice.enums.WebSocketErrorCode;
import com.gopair.websocketservice.protocol.MessageType;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import com.gopair.websocketservice.service.ConnectionManagerService;
import com.gopair.websocketservice.service.BasicSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * WebSocket连接处理器
 * 专门负责连接建立和断开的逻辑处理
 * 
 * @author gopair
 */
@Slf4j
@Component
public class ConnectionHandler {

    private final ConnectionManagerService connectionManager;
    private final BasicSubscriptionService basicSubscriptionService;
    private final ObjectMapper objectMapper;
    
    public ConnectionHandler(ConnectionManagerService connectionManager,
                           @Lazy BasicSubscriptionService basicSubscriptionService,
                           ObjectMapper objectMapper) {
        this.connectionManager = connectionManager;
        this.basicSubscriptionService = basicSubscriptionService;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理连接建立
     * 
     * @param session WebSocket会话
     * @param userInfo 用户信息
     * @return 是否建立成功
     */
    public boolean handleConnectionEstablished(WebSocketSession session, Map<String, Object> userInfo) {
        try {
            log.info("[连接管理] 开始建立WebSocket连接: sessionId={}", session.getId());

            if (!(Boolean) userInfo.get("valid")) {
                String errorReason = (String) userInfo.get("errorReason");
                log.warn("[连接管理] 用户信息验证失败: sessionId={}, reason={}", session.getId(), errorReason);
                return false;
            }

            Long userId = Long.valueOf(userInfo.get("userId").toString());
            String nickname = (String) userInfo.get("nickname");

            // 建立全局连接
            connectionManager.addGlobalSession(session, userId);

            // 执行登录基础订阅
            basicSubscriptionService.performLoginBasicSubscription(session.getId(), userId);

            // 发送连接成功确认
            UnifiedWebSocketMessage welcomeMessage = new UnifiedWebSocketMessage()
                    .setMessageId(UUID.randomUUID().toString())
                    .setTimestamp(LocalDateTime.now())
                    .setType(MessageType.CONNECTION)
                    .setEventType("connected")
                    .setPayload(Map.of(
                            "userId", userId,
                            "nickname", nickname,
                            "connectionTime", LocalDateTime.now()
                    ));

            sendWelcomeMessage(session, welcomeMessage);
            
            log.info("[连接管理] WebSocket连接建立成功: sessionId={}, userId={}, nickname={}", 
                    session.getId(), userId, nickname);

            return true;

        } catch (Exception e) {
            log.error("[连接管理] 建立WebSocket连接失败: sessionId={}", session.getId(), e);
            return false;
        }
    }

    /**
     * 处理连接断开
     * 
     * @param session WebSocket会话
     */
    public void handleConnectionClosed(WebSocketSession session) {
        try {
            log.info("[连接管理] WebSocket连接断开: sessionId={}", session.getId());
            
            // 清理连接相关资源
            connectionManager.removeSession(session.getId());
            
            log.info("[连接管理] 连接资源清理完成: sessionId={}", session.getId());
            
        } catch (Exception e) {
            log.error("[连接管理] 处理连接断开失败: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 发送欢迎消息
     */
    private void sendWelcomeMessage(WebSocketSession session, UnifiedWebSocketMessage message) {
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            session.sendMessage(new org.springframework.web.socket.TextMessage(messageJson));
            log.debug("[连接管理] 发送欢迎消息成功: sessionId={}", session.getId());
        } catch (Exception e) {
            log.error("[连接管理] 发送欢迎消息失败: sessionId={}", session.getId(), e);
        }
    }
} 