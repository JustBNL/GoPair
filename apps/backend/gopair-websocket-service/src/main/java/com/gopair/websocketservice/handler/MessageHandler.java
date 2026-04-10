package com.gopair.websocketservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.websocketservice.domain.payload.SubscriptionPayload;
import com.gopair.websocketservice.exception.PayloadAdaptationException;
import com.gopair.websocketservice.protocol.MessageType;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import com.gopair.websocketservice.service.SubscriptionManagerService;
import com.gopair.websocketservice.service.ChannelMessageRouter;
import com.gopair.websocketservice.service.ConnectionManagerService;
import com.gopair.websocketservice.util.PayloadAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageHandler {

    private final SubscriptionManagerService subscriptionManager;
    private final ChannelMessageRouter channelMessageRouter;
    private final ConnectionManagerService connectionManager;
    private final ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    public boolean handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            log.debug("[消息处理] 收到消息: sessionId={}", session.getId());
            
            String payload = message.getPayload();
            Map<String, Object> messageMap = objectMapper.readValue(payload, Map.class);
            
            UnifiedWebSocketMessage wsMessage = convertToUnifiedMessage(messageMap);
            return routeMessage(session, wsMessage);
            
        } catch (Exception e) {
            log.error("[消息处理] 处理文本消息失败: sessionId={}", session.getId(), e);
            return false;
        }
    }

    private boolean routeMessage(WebSocketSession session, UnifiedWebSocketMessage message) {
        try {
            return switch (message.getType()) {
                case SUBSCRIBE -> handleSubscriptionMessage(session, message);
                case CHANNEL_MESSAGE -> handleChannelMessage(session, message);
                case HEARTBEAT -> handleHeartbeatMessage(session, message);
                default -> {
                    log.warn("[消息处理] 不支持的消息类型: sessionId={}, type={}",
                            session.getId(), message.getType());
                    yield false;
                }
            };
        } catch (Exception e) {
            log.error("[消息处理] 消息路由失败: sessionId={}", session.getId(), e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private UnifiedWebSocketMessage convertToUnifiedMessage(Map<String, Object> messageMap) {
        UnifiedWebSocketMessage message = new UnifiedWebSocketMessage();
        
        message.setMessageId((String) messageMap.get("messageId"));
        message.setEventType((String) messageMap.get("eventType"));
        message.setAction((String) messageMap.get("action"));

        // 解析 payload：优先取顶层 payload，其次取 data.payload，最后取 data
        Map<String, Object> payload = (Map<String, Object>) messageMap.get("payload");
        if (payload == null) {
            Map<String, Object> data = (Map<String, Object>) messageMap.get("data");
            payload = data != null ? (Map<String, Object>) data.get("payload") : data;
        }
        message.setPayload(payload);

        // 解析 channel：优先从 payload 中取，其次从顶层取
        // 前端 buildSubscribeMessage 将 channel 放在 data.payload.channel
        String channel = null;
        if (payload != null) {
            channel = (String) payload.get("channel");
        }
        if (channel == null) {
            channel = (String) messageMap.get("channel");
        }
        message.setChannel(channel);
        
        Object timestamp = messageMap.get("timestamp");
        if (timestamp != null) {
            try {
                // 兼容带时区后缀的 ISO 8601 格式（如 "2026-03-15T04:22:44.523Z" 或 "+08:00"）
                message.setTimestamp(java.time.OffsetDateTime.parse(timestamp.toString()).toLocalDateTime());
            } catch (Exception e1) {
                try {
                    // 降级：尝试解析不带时区的本地时间格式
                    message.setTimestamp(java.time.LocalDateTime.parse(timestamp.toString()));
                } catch (Exception e2) {
                    message.setTimestamp(java.time.LocalDateTime.now());
                }
            }
        } else {
            message.setTimestamp(java.time.LocalDateTime.now());
        }
        
        String typeStr = (String) messageMap.get("type");
        message.setType(typeStr != null ? parseMessageType(typeStr) : MessageType.CHAT);
        
        return message;
    }

    private MessageType parseMessageType(String typeStr) {
        return switch (typeStr) {
            case "subscribe" -> MessageType.SUBSCRIBE;
            case "heartbeat" -> MessageType.HEARTBEAT;
            case "room_message" -> MessageType.CHAT;
            default -> MessageType.fromValue(typeStr);
        };
    }

    private boolean handleSubscriptionMessage(WebSocketSession session, UnifiedWebSocketMessage message) {
        String eventType = message.getEventType();
        Map<String, Object> payload = message.getPayload();

        return switch (eventType) {
            case "subscribe" -> {
                try {
                    SubscriptionPayload subPayload = PayloadAdapter.forSubscription(payload);
                    if (!subPayload.isValid()) {
                        log.warn("[消息处理] 无效的订阅载荷: sessionId={}", session.getId());
                        yield false;
                    }
                    
                    boolean result = subscriptionManager.subscribeChannel(
                            session.getId(), subPayload.getUserId(), subPayload.getChannel(),
                            subPayload.getEventTypes(), subPayload.getSource());
                    
                    log.info("[消息处理] 用户订阅频道成功: sessionId={}, userId={}, channel={}", 
                            session.getId(), subPayload.getUserId(), subPayload.getChannel());
                    yield result;
                } catch (PayloadAdaptationException e) {
                    log.error("[消息处理] 订阅载荷适配失败: sessionId={}", session.getId(), e);
                    yield false;
                }
            }
            case "unsubscribe" -> {
                try {
                    Long userId = PayloadAdapter.extractLongValue(payload, "userId", true);
                    String channel = PayloadAdapter.extractStringValue(payload, "channel", true);
                    
                    subscriptionManager.unsubscribeChannel(session.getId(), userId, channel);
                    log.info("[消息处理] 用户取消订阅频道成功: sessionId={}, userId={}, channel={}", 
                            session.getId(), userId, channel);
                    yield true;
                } catch (PayloadAdaptationException e) {
                    log.error("[消息处理] 取消订阅载荷适配失败: sessionId={}", session.getId(), e);
                    yield false;
                }
            }
            default -> {
                log.warn("[消息处理] 不支持的订阅事件类型: sessionId={}, eventType={}", 
                        session.getId(), eventType);
                yield false;
            }
        };
    }

    private boolean handleChannelMessage(WebSocketSession session, UnifiedWebSocketMessage message) {
        try {
            log.info("[消息处理] 收到客户端频道消息: sessionId={}, channel={}, eventType={}",
                    session.getId(), message.getChannel(), message.getEventType());

            // 将客户端频道消息统一交给频道路由服务按订阅关系进行路由
            channelMessageRouter.processChannelMessage(message);
            return true;
        } catch (Exception e) {
            log.error("[消息处理] 处理频道消息失败: sessionId={}", session.getId(), e);
            return false;
        }
    }

    private boolean handleHeartbeatMessage(WebSocketSession session, UnifiedWebSocketMessage message) {
        try {
            connectionManager.updateHeartbeat(session.getId());

            UnifiedWebSocketMessage response = new UnifiedWebSocketMessage()
                    .setMessageId(message.getMessageId())
                    .setType(MessageType.HEARTBEAT)
                    .setEventType("pong")
                    .setPayload(Map.of());

            sendResponse(session, response);
            return true;
        } catch (Exception e) {
            log.error("[消息处理] 处理心跳消息失败: sessionId={}", session.getId(), e);
            return false;
        }
    }

    private void sendResponse(WebSocketSession session, UnifiedWebSocketMessage response) throws Exception {
        String jsonMessage = objectMapper.writeValueAsString(response);
        session.sendMessage(new TextMessage(jsonMessage));
    }
}
