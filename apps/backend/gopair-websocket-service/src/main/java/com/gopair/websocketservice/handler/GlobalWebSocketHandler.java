package com.gopair.websocketservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.websocketservice.enums.WebSocketErrorCode;
import com.gopair.websocketservice.protocol.MessageType;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import com.gopair.websocketservice.service.ConnectionManagerService;
import com.gopair.websocketservice.service.BasicSubscriptionService;
import com.gopair.websocketservice.service.SubscriptionManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 全局WebSocket处理器（重构版）
 * 现在作为协调器，将具体职责委托给专门的Handler类：
 * - ConnectionHandler：处理连接建立和断开
 * - MessageHandler：处理消息路由和转发
 * - ErrorHandler：处理错误响应和异常处理
 * 
 * 本类仅保留频率限制和用户信息提取等核心逻辑
 * 
 * @author gopair
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalWebSocketHandler implements WebSocketHandler {

    private final ConnectionManagerService connectionManager;
    private final SubscriptionManagerService subscriptionManager;
    private final BasicSubscriptionService basicSubscriptionService;
    private final ObjectMapper objectMapper;
    
    // 新的Handler组件
    private final ConnectionHandler connectionHandler;
    private final MessageHandler messageHandler;
    private final ErrorHandler errorHandler;

    /**
     * 频率限制：每用户每秒最多5条消息
     */
    private final Map<Long, MessageRateLimit> userRateLimits = new HashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            log.info("[全局处理器] 开始建立WebSocket连接: sessionId={}", session.getId());

            // 1. 从网关传递的请求头获取用户信息
            Map<String, Object> userInfo = extractUserInfoFromHeaders(session);
            
            // 2. 使用ConnectionHandler处理连接建立
            boolean success = connectionHandler.handleConnectionEstablished(session, userInfo);
            
            if (!success) {
                String errorReason = (String) userInfo.get("errorReason");
                errorHandler.sendErrorAndClose(session, WebSocketErrorCode.USER_INFO_HEADER_MISSING, errorReason);
                return;
            }

            // 3. 初始化频率限制
            Long userId = Long.valueOf(userInfo.get("userId").toString());
            userRateLimits.put(userId, new MessageRateLimit());

            log.info("[全局处理器] WebSocket连接建立成功: sessionId={}, userId={}", 
                    session.getId(), userId);

        } catch (Exception e) {
            log.error("[全局处理器] 建立WebSocket连接失败: sessionId={}", session.getId(), e);
            errorHandler.sendErrorAndClose(session, WebSocketErrorCode.CONNECTION_ESTABLISH_FAILED, 
                    "连接建立失败: " + e.getMessage());
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        try {
            if (message instanceof TextMessage) {
                // 获取用户信息进行频率限制检查
                ConnectionManagerService.SessionInfo sessionInfo = connectionManager.getSessionInfo(session.getId());
                if (sessionInfo == null) {
                    errorHandler.sendErrorMessage(session, WebSocketErrorCode.SESSION_NOT_FOUND, "会话信息不存在");
                    return;
                }

                Long userId = sessionInfo.getUserId();
                
                // 频率限制检查
                if (!checkRateLimit(userId)) {
                    log.warn("[全局处理器] 消息发送频率超限: userId={}, sessionId={}", userId, session.getId());
                    errorHandler.sendErrorMessage(session, WebSocketErrorCode.MESSAGE_PROCESSING_ERROR, 
                            "消息发送频率超限，请稍后再试");
                    return;
                }

                // 使用MessageHandler处理文本消息
                boolean success = messageHandler.handleTextMessage(session, (TextMessage) message);
                if (!success) {
                    errorHandler.handleMessageProcessingError(session, ((TextMessage) message).getPayload(), 
                            new RuntimeException("消息处理失败"));
                }
            } else {
                log.warn("[全局处理器] 收到不支持的消息类型: sessionId={}, messageType={}", 
                        session.getId(), message.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.error("[全局处理器] 处理WebSocket消息失败: sessionId={}", session.getId(), e);
            errorHandler.sendErrorMessage(session, WebSocketErrorCode.MESSAGE_PROCESSING_ERROR, 
                    "消息处理失败: " + e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.info("[全局处理器] WebSocket传输错误，委托给ErrorHandler处理: sessionId={}", session.getId());
        
        // 使用ErrorHandler处理传输错误
        errorHandler.handleTransportError(session, exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String sessionId = session.getId();
        log.info("[全局处理器] WebSocket连接关闭: sessionId={}, status={}", sessionId, closeStatus);

        try {
            // 获取会话信息
            ConnectionManagerService.SessionInfo sessionInfo = connectionManager.getSessionInfo(sessionId);
            if (sessionInfo != null) {
                Long userId = sessionInfo.getUserId();
                
                // 清理频率限制
                if (userId != null) {
                    userRateLimits.remove(userId);
                }
            }

            // 使用ConnectionHandler处理连接关闭
            connectionHandler.handleConnectionClosed(session);

        } catch (Exception e) {
            log.error("[全局处理器] 清理WebSocket连接失败: sessionId={}", sessionId, e);
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * 从请求头提取用户信息
     */
    private Map<String, Object> extractUserInfoFromHeaders(WebSocketSession session) {
        try {
            String userId = getHeaderValue(session, "X-User-Id");
            String nickname = getHeaderValue(session, "X-Nickname");

            if (userId == null || userId.trim().isEmpty()) {
                return Map.of("valid", false, "errorReason", "用户ID请求头缺失");
            }

            if (nickname == null || nickname.trim().isEmpty()) {
                return Map.of("valid", false, "errorReason", "用户昵称请求头缺失");
            }

            try {
                Long.valueOf(userId); // 验证userId格式
            } catch (NumberFormatException e) {
                return Map.of("valid", false, "errorReason", "用户ID格式错误");
            }

            return Map.of("userId", userId, "nickname", nickname, "valid", true);

        } catch (Exception e) {
            log.error("[全局处理器] 提取用户信息失败: sessionId={}", session.getId(), e);
            return Map.of("valid", false, "errorReason", "用户信息提取失败");
        }
    }

    /**
     * 获取请求头值
     */
    private String getHeaderValue(WebSocketSession session, String headerName) {
        try {
            List<String> headerValues = session.getHandshakeHeaders().get(headerName);
            return (headerValues != null && !headerValues.isEmpty()) ? headerValues.get(0) : null;
        } catch (Exception e) {
            log.error("[全局处理器] 获取请求头失败: sessionId={}, headerName={}", session.getId(), headerName, e);
            return null;
        }
    }

    /**
     * 检查用户消息发送频率限制
     */
    private boolean checkRateLimit(Long userId) {
        MessageRateLimit rateLimit = userRateLimits.get(userId);
        if (rateLimit == null) {
            return true;
        }

        return rateLimit.tryAcquire();
    }

    /**
     * 消息频率限制内部类
     */
    private static class MessageRateLimit {
        private final long[] timestamps = new long[5]; // 最多5条/秒
        private int index = 0;

        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            long oldestAllowed = now - TimeUnit.SECONDS.toMillis(1);

            // 检查当前时间窗口内的消息数
            int count = 0;
            for (long timestamp : timestamps) {
                if (timestamp > oldestAllowed) {
                    count++;
                }
            }

            if (count >= 5) {
                return false; // 超过频率限制
            }

            // 记录当前消息时间戳
            timestamps[index] = now;
            index = (index + 1) % timestamps.length;
            return true;
        }
    }
} 