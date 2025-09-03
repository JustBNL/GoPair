package com.gopair.websocketservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.websocketservice.protocol.MessageType;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import com.gopair.websocketservice.service.SubscriptionManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * WebSocket消息处理器
 * 专门负责消息路由和转发的逻辑处理
 * 
 * @author gopair
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageHandler {

    private final SubscriptionManagerService subscriptionManager;
    private final ObjectMapper objectMapper;

    /**
     * 处理文本消息
     * 
     * @param session WebSocket会话
     * @param message 文本消息
     * @return 是否处理成功
     */
    public boolean handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            log.debug("[消息处理] 收到消息: sessionId={}", session.getId());
            
            String payload = message.getPayload();
            UnifiedWebSocketMessage wsMessage = objectMapper.readValue(payload, UnifiedWebSocketMessage.class);
            
            return routeMessage(session, wsMessage);
            
        } catch (Exception e) {
            log.error("[消息处理] 处理文本消息失败: sessionId={}", session.getId(), e);
            return false;
        }
    }

    /**
     * 路由消息到对应的处理器
     * 
     * @param session WebSocket会话
     * @param message 统一消息对象
     * @return 是否路由成功
     */
    private boolean routeMessage(WebSocketSession session, UnifiedWebSocketMessage message) {
        try {
            MessageType messageType = message.getType();
            String eventType = message.getEventType();
            
            log.debug("[消息处理] 路由消息: sessionId={}, type={}, eventType={}", 
                    session.getId(), messageType, eventType);

            switch (messageType) {
                case SUBSCRIBE:
                    return handleSubscriptionMessage(session, message);
                case CHANNEL_MESSAGE:
                    return handleChannelMessage(session, message);
                case HEARTBEAT:
                    return handleHeartbeatMessage(session, message);
                default:
                    log.warn("[消息处理] 不支持的消息类型: sessionId={}, type={}", 
                            session.getId(), messageType);
                    return false;
            }
            
        } catch (Exception e) {
            log.error("[消息处理] 消息路由失败: sessionId={}", session.getId(), e);
            return false;
        }
    }

    /**
     * 处理订阅消息
     */
    private boolean handleSubscriptionMessage(WebSocketSession session, UnifiedWebSocketMessage message) {
        try {
            String eventType = message.getEventType();
            Map<String, Object> payload = message.getPayload();
            
            switch (eventType) {
                case "subscribe":
                    String channel = (String) payload.get("channel");
                    Long userId = (Long) payload.get("userId");
                    subscriptionManager.subscribeChannel(session.getId(), userId, channel, 
                            java.util.Set.of("default"), "manual");
                    log.info("[消息处理] 用户订阅频道: sessionId={}, channel={}", session.getId(), channel);
                    return true;
                    
                case "unsubscribe":
                    String unsubChannel = (String) payload.get("channel");
                    Long unsubUserId = (Long) payload.get("userId");
                    subscriptionManager.unsubscribeChannel(session.getId(), unsubUserId, unsubChannel);
                    log.info("[消息处理] 用户取消订阅频道: sessionId={}, channel={}", session.getId(), unsubChannel);
                    return true;
                    
                default:
                    log.warn("[消息处理] 不支持的订阅事件类型: sessionId={}, eventType={}", 
                            session.getId(), eventType);
                    return false;
            }
            
        } catch (Exception e) {
            log.error("[消息处理] 处理订阅消息失败: sessionId={}", session.getId(), e);
            return false;
        }
    }

    /**
     * 处理频道消息
     */
    private boolean handleChannelMessage(WebSocketSession session, UnifiedWebSocketMessage message) {
        try {
            log.debug("[消息处理] 处理频道消息: sessionId={}, messageId={}", 
                    session.getId(), message.getMessageId());
            
            // TODO: 实现频道消息转发到RabbitMQ的逻辑
            log.info("[消息处理] 频道消息处理成功: sessionId={}", session.getId());
            return true;
            
        } catch (Exception e) {
            log.error("[消息处理] 处理频道消息失败: sessionId={}", session.getId(), e);
            return false;
        }
    }

    /**
     * 处理心跳消息
     */
    private boolean handleHeartbeatMessage(WebSocketSession session, UnifiedWebSocketMessage message) {
        try {
            log.debug("[消息处理] 处理心跳消息: sessionId={}", session.getId());
            
            // 简单返回心跳响应
            UnifiedWebSocketMessage response = new UnifiedWebSocketMessage()
                    .setMessageId(message.getMessageId())
                    .setType(MessageType.HEARTBEAT)
                    .setEventType("pong");
            
            sendResponse(session, response);
            return true;
            
        } catch (Exception e) {
            log.error("[消息处理] 处理心跳消息失败: sessionId={}", session.getId(), e);
            return false;
        }
    }

    /**
     * 发送响应消息
     */
    private void sendResponse(WebSocketSession session, UnifiedWebSocketMessage response) {
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            session.sendMessage(new TextMessage(responseJson));
            log.debug("[消息处理] 发送响应成功: sessionId={}", session.getId());
        } catch (Exception e) {
            log.error("[消息处理] 发送响应失败: sessionId={}", session.getId(), e);
        }
    }
} 