package com.gopair.websocketservice.handler;

import com.gopair.websocketservice.enums.WebSocketErrorCode;
import com.gopair.websocketservice.protocol.MessageType;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import com.gopair.websocketservice.service.ConnectionManagerService;
import com.gopair.websocketservice.service.BasicSubscriptionService;
import com.gopair.websocketservice.service.SubscriptionManagerService;
import com.gopair.websocketservice.service.BasicRateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 全局WebSocket处理器
 * 
 * 职责：
 * - 作为WebSocket连接的统一入口点
 * - 协调连接、消息、错误三个专门的处理器
 * - 集成频率限制功能
 * 
 * 架构设计：
 * - 采用单一职责原则，将不同功能委托给专门的Handler类
 * - ConnectionHandler：处理连接建立和断开
 * - MessageHandler：处理消息路由和转发
 * - ErrorHandler：处理错误响应和异常处理
 * - BasicRateLimitService：统一管理频率限制
 * 
 * 性能优化：
 * - 移除内部频率限制实现，使用统一的RateLimitService
 * - 减少内存占用和代码重复
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
    private final BasicRateLimitService basicRateLimitService;
    
    private final ConnectionHandler connectionHandler;
    private final MessageHandler messageHandler;
    private final ErrorHandler errorHandler;

    /**
     * WebSocket连接建立后的回调
     * 
     * 流程：
     * 1. 从网关传递的请求头获取用户信息
     * 2. 使用ConnectionHandler处理连接建立
     * 3. 如果建立失败，发送错误消息并关闭连接
     * 
     * @param session WebSocket会话
     * @throws Exception 连接建立异常
     */
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

            log.info("[全局处理器] WebSocket连接建立成功: sessionId={}, userId={}", 
                    session.getId(), userInfo.get("userId"));

        } catch (Exception e) {
            log.error("[全局处理器] 建立WebSocket连接失败: sessionId={}", session.getId(), e);
            errorHandler.sendErrorAndClose(session, WebSocketErrorCode.CONNECTION_ESTABLISH_FAILED, 
                    "连接建立失败: " + e.getMessage());
        }
    }

    /**
     * 处理WebSocket消息
     * 
     * 流程：
     * 1. 检查消息类型（仅支持文本消息）
     * 2. 获取会话信息进行频率限制检查
     * 3. 使用BasicRateLimitService进行频率限制
     * 4. 使用MessageHandler处理文本消息
     * 5. 如果处理失败，发送错误消息
     * 
     * @param session WebSocket会话
     * @param message WebSocket消息
     * @throws Exception 消息处理异常
     */
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
                
                // 使用BasicRateLimitService进行频率限制检查
                if (!basicRateLimitService.checkMessageRateLimit(userId)) {
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

    /**
     * 处理WebSocket传输错误
     * 
     * 流程：
     * 1. 记录传输错误日志
     * 2. 委托给ErrorHandler处理传输错误
     * 
     * @param session WebSocket会话
     * @param exception 异常信息
     * @throws Exception 传输错误处理异常
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.info("[全局处理器] WebSocket传输错误，委托给ErrorHandler处理: sessionId={}", session.getId());
        
        // 使用ErrorHandler处理传输错误
        errorHandler.handleTransportError(session, exception);
    }

    /**
     * WebSocket连接关闭后的回调
     * 
     * 流程：
     * 1. 记录连接关闭日志
     * 2. 获取会话信息
     * 3. 清理频率限制（委托给BasicRateLimitService）
     * 4. 使用ConnectionHandler处理连接关闭
     * 
     * @param session WebSocket会话
     * @param closeStatus 关闭状态
     * @throws Exception 连接关闭处理异常
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String sessionId = session.getId();
        log.info("[全局处理器] WebSocket连接关闭: sessionId={}, status={}", sessionId, closeStatus);

        try {
            // 获取会话信息
            ConnectionManagerService.SessionInfo sessionInfo = connectionManager.getSessionInfo(sessionId);
            if (sessionInfo != null) {
                Long userId = sessionInfo.getUserId();
                
                // 清理频率限制（委托给BasicRateLimitService）
                if (userId != null) {
                    basicRateLimitService.resetUserMessageRate(userId);
                }
            }

            // 使用ConnectionHandler处理连接关闭
            connectionHandler.handleConnectionClosed(session);

        } catch (Exception e) {
            log.error("[全局处理器] 清理WebSocket连接失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 是否支持部分消息
     * 
     * @return false（不支持部分消息）
     */
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
     * 
     * @param session WebSocket会话
     * @param headerName 请求头名称
     * @return 请求头值，不存在时返回null
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
} 