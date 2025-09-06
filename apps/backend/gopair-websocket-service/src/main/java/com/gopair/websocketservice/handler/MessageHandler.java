package com.gopair.websocketservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.websocketservice.domain.payload.SubscriptionPayload;
import com.gopair.websocketservice.exception.PayloadAdaptationException;
// import removed: ClientWebSocketMessage not used
import com.gopair.websocketservice.protocol.MessageType;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import com.gopair.websocketservice.service.SubscriptionManagerService;
import com.gopair.websocketservice.util.PayloadAdapter;
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
    @SuppressWarnings("unchecked")
    public boolean handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            log.debug("[消息处理] 收到消息: sessionId={}", session.getId());
            
            String payload = message.getPayload();
            
            // 直接使用Map反序列化，避开所有类型包装器问题
            Map<String, Object> messageMap = objectMapper.readValue(payload, Map.class);
            log.debug("[消息处理] 消息解析成功: type={}, eventType={}", 
                    messageMap.get("type"), messageMap.get("eventType"));
            
            // 手动转换为UnifiedWebSocketMessage
            UnifiedWebSocketMessage wsMessage = convertMapToUnifiedMessage(messageMap);
            
            return routeMessage(session, wsMessage);
            
        } catch (Exception e) {
            log.error("[消息处理] 处理文本消息失败: sessionId={}, payload={}", session.getId(), message.getPayload(), e);
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
     * 将Map转换为UnifiedWebSocketMessage
     */
    @SuppressWarnings("unchecked")
    private UnifiedWebSocketMessage convertMapToUnifiedMessage(Map<String, Object> messageMap) {
        UnifiedWebSocketMessage message = new UnifiedWebSocketMessage();
        
        // 基本字段
        message.setMessageId((String) messageMap.get("messageId"));
        message.setEventType((String) messageMap.get("eventType"));
        message.setAction((String) messageMap.get("action"));
        
        // 处理channel
        String channel = (String) messageMap.get("channel");
        if (channel == null && messageMap.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) messageMap.get("data");
            if (data != null) {
                channel = (String) data.get("channel");
            }
        }
        message.setChannel(channel);
        
        // 处理payload
        Map<String, Object> payload = null;
        if (messageMap.containsKey("payload")) {
            payload = (Map<String, Object>) messageMap.get("payload");
        } else if (messageMap.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) messageMap.get("data");
            if (data != null && data.containsKey("payload")) {
                payload = (Map<String, Object>) data.get("payload");
            } else {
                payload = data;
            }
        }
        message.setPayload(payload);
        
        // 处理时间戳
        Object timestampObj = messageMap.get("timestamp");
        if (timestampObj != null) {
            try {
                String timestamp = timestampObj.toString();
                message.setTimestamp(java.time.LocalDateTime.parse(timestamp, 
                    java.time.format.DateTimeFormatter.ISO_DATE_TIME));
            } catch (Exception e) {
                message.setTimestamp(java.time.LocalDateTime.now());
            }
        } else {
            message.setTimestamp(java.time.LocalDateTime.now());
        }
        
        // 处理消息类型
        String typeStr = (String) messageMap.get("type");
        if (typeStr != null) {
            try {
                message.setType(MessageType.fromValue(typeStr));
            } catch (IllegalArgumentException e) {
                // 处理前端发送的特殊类型值
                switch (typeStr) {
                    case "subscribe":
                        message.setType(MessageType.SUBSCRIBE);
                        break;
                    case "heartbeat":
                        message.setType(MessageType.HEARTBEAT);
                        break;
                    case "room_message":
                        message.setType(MessageType.CHAT);
                        break;
                    default:
                        message.setType(MessageType.CHAT);
                }
            }
        } else {
            message.setType(MessageType.CHAT);
        }
        
        return message;
    }

    /**
     * 处理订阅消息
     */
    @SuppressWarnings("unchecked")
    private boolean handleSubscriptionMessage(WebSocketSession session, UnifiedWebSocketMessage message) {
        try {
            String eventType = message.getEventType();
            Map<String, Object> payload = message.getPayload();
            
            switch (eventType) {
                case "subscribe":
                    try {
                        // 使用PayloadAdapter进行类型安全转换
                        SubscriptionPayload subscriptionPayload = PayloadAdapter.forSubscription(payload);
                        
                        // 验证载荷有效性
                        if (!subscriptionPayload.isValid()) {
                            log.warn("[消息处理] 无效的订阅载荷: sessionId={}, payload={}", 
                                    session.getId(), payload);
                            return false;
                        }
                        
                        boolean subscriptionResult = subscriptionManager.subscribeChannel(
                                session.getId(), 
                                subscriptionPayload.getUserId(), 
                                subscriptionPayload.getChannel(), 
                                subscriptionPayload.getEventTypes(), 
                                subscriptionPayload.getSource());
                        
                        if (!subscriptionResult) {
                            log.warn("[消息处理] 用户订阅频道失败: sessionId={}, userId={}, channel={}, eventTypes={}", 
                                    session.getId(), 
                                    subscriptionPayload.getUserIdString(),
                                    subscriptionPayload.getChannel(), 
                                    subscriptionPayload.getEventTypes());
                            return false;
                        }
                                
                        log.info("[消息处理] 用户订阅频道成功: sessionId={}, userId={}, channel={}, eventTypes={}", 
                                session.getId(), 
                                subscriptionPayload.getUserIdString(),
                                subscriptionPayload.getChannel(), 
                                subscriptionPayload.getEventTypes());
                        return true;
                        
                    } catch (PayloadAdaptationException e) {
                        log.error("[消息处理] 订阅载荷适配失败: sessionId={}, error={}", 
                                session.getId(), e.getMessage());
                        return false;
                    }
                    
                case "unsubscribe":
                    try {
                        // 使用PayloadAdapter安全提取userId和channel
                        Long unsubUserId = PayloadAdapter.extractLongValue(payload, "userId", true);
                        String unsubChannel = PayloadAdapter.extractStringValue(payload, "channel", true);
                        
                        subscriptionManager.unsubscribeChannel(session.getId(), unsubUserId, unsubChannel);
                        log.info("[消息处理] 用户取消订阅频道成功: sessionId={}, userId={}, channel={}", 
                                session.getId(), unsubUserId, unsubChannel);
                        return true;
                        
                    } catch (PayloadAdaptationException e) {
                        log.error("[消息处理] 取消订阅载荷适配失败: sessionId={}, error={}", 
                                session.getId(), e.getMessage());
                        return false;
                    }
                    
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
                    .setEventType("pong")
                    .setPayload(java.util.Map.of());
            
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