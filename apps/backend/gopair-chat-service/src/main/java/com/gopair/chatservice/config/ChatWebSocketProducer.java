package com.gopair.chatservice.config;

import com.gopair.common.constants.SystemConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 私聊 WebSocket 消息生产者。
 *
 * <p>封装 RabbitMQ 发送逻辑，将私聊消息推送至 WebSocket 服务。
 * 与 WebSocketMessageProducer 同理，但 channel 固定为 user:X，type 为 chat，
 * 使前端能够通过 chatType='private' 区分私聊消息和房间消息。
 *
 * @author gopair
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送私聊消息到指定用户的 WebSocket 频道。
     *
     * <p>channel = "user:{userId}"，type = "chat"，eventType = "message_send"。
     * payload 中携带 chatType = "private"，前端据此区分消息类型。
     *
     * @param userId  目标用户ID
     * @param payload 消息载荷，应包含 messageId、senderId、content 等字段
     */
    public void sendPrivateMessage(Long userId, Map<String, Object> payload) {
        Map<String, Object> enriched = new java.util.HashMap<>(payload);
        enriched.put("chatType", "private");

        ChatMessageDto message = new ChatMessageDto(
            UUID.randomUUID().toString(),
            LocalDateTime.now(),
            "chat",
            SystemConstants.CHANNEL_USER_PREFIX + userId,
            "message_send",
            enriched,
            System.getProperty("spring.application.name", "chat-service")
        );

        String channel = SystemConstants.CHANNEL_USER_PREFIX + userId;
        log.info("[ChatWS] 准备发送私聊WebSocket消息: userId={}, channel={}, routingKey={}, messageId={}, payload.chatType={}, payloadKeys={}", userId, channel, SystemConstants.ROUTING_KEY_SYSTEM_USER, message.messageId, enriched.get("chatType"), enriched.keySet());

        rabbitTemplate.convertAndSend(
            SystemConstants.WEBSOCKET_EXCHANGE,
            SystemConstants.ROUTING_KEY_SYSTEM_USER,
            message
        );
        log.info("[ChatWS] 发送私聊消息到用户: userId={}, channel={}, eventType={}, messageId={}", userId, channel, "message_send", message.messageId);
    }

    /**
     * 发送好友请求通知到指定用户的 WebSocket 频道。
     *
     * <p>type = "system"，eventType = "friend_request"，前端据此展示通知。
     */
    public void sendFriendRequestNotification(Long userId, Map<String, Object> payload) {
        Map<String, Object> enriched = new java.util.HashMap<>(payload);
        enriched.put("notifyType", "friend_request");

        ChatMessageDto message = new ChatMessageDto(
            UUID.randomUUID().toString(),
            LocalDateTime.now(),
            "system",
            SystemConstants.CHANNEL_USER_PREFIX + userId,
            "friend_request",
            enriched,
            System.getProperty("spring.application.name", "chat-service")
        );

        rabbitTemplate.convertAndSend(
            SystemConstants.WEBSOCKET_EXCHANGE,
            SystemConstants.ROUTING_KEY_SYSTEM_USER,
            message
        );
        log.debug("发送好友请求通知: userId={}", userId);
    }

    /**
     * 发送好友状态变更通知（如被删除）。
     */
    public void sendFriendStatusNotification(Long userId, Map<String, Object> payload) {
        Map<String, Object> enriched = new java.util.HashMap<>(payload);
        enriched.put("notifyType", "friend_status");

        ChatMessageDto message = new ChatMessageDto(
            UUID.randomUUID().toString(),
            LocalDateTime.now(),
            "system",
            SystemConstants.CHANNEL_USER_PREFIX + userId,
            "friend_status",
            enriched,
            System.getProperty("spring.application.name", "chat-service")
        );

        rabbitTemplate.convertAndSend(
            SystemConstants.WEBSOCKET_EXCHANGE,
            SystemConstants.ROUTING_KEY_SYSTEM_USER,
            message
        );
        log.debug("发送好友状态通知: userId={}", userId);
    }

    public record ChatMessageDto(
        String messageId,
        LocalDateTime timestamp,
        String type,
        String channel,
        String eventType,
        Map<String, Object> payload,
        String source
    ) {}
}
