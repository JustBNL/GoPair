package com.gopair.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * WebSocket消息生产者
 * 供业务服务向WebSocket服务发送消息
 *
 * @author gopair
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnClass(RabbitTemplate.class)
public class WebSocketMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    private static final String WEBSOCKET_EXCHANGE = "websocket.topic";

    /**
     * 发送聊天消息到房间
     *
     * @param roomId  房间ID
     * @param payload 消息载荷
     */
    public void sendChatMessageToRoom(Long roomId, Map<String, Object> payload) {
        WebSocketMessageDto message = WebSocketMessageDto.builder()
                .messageId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .type("chat")
                .channel("room:" + roomId)
                .eventType("message_send")
                .payload(payload)
                .source(getServiceName())
                .build();

        rabbitTemplate.convertAndSend(WEBSOCKET_EXCHANGE, "chat.room", message);
        log.debug("发送聊天消息到房间: roomId={}, messageId={}", roomId, message.getMessageId());
    }

    /**
     * 发送消息给特定用户
     *
     * @param userId  用户ID
     * @param payload 消息载荷
     */
    public void sendMessageToUser(Long userId, Map<String, Object> payload) {
        WebSocketMessageDto message = WebSocketMessageDto.builder()
                .messageId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .type("system")
                .channel("user:" + userId)
                .eventType("notification")
                .payload(payload)
                .source(getServiceName())
                .build();

        rabbitTemplate.convertAndSend(WEBSOCKET_EXCHANGE, "system.user", message);
        log.debug("发送消息给用户: userId={}, messageId={}", userId, message.getMessageId());
    }

    /**
     * 发送WebRTC信令消息
     *
     * @param userId  目标用户ID
     * @param payload 信令载荷
     */
    public void sendSignalingMessage(Long userId, Map<String, Object> payload) {
        WebSocketMessageDto message = WebSocketMessageDto.builder()
                .messageId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .type("signaling")
                .channel("user:" + userId)
                .eventType("signaling")
                .payload(payload)
                .source(getServiceName())
                .build();

        rabbitTemplate.convertAndSend(WEBSOCKET_EXCHANGE, "signaling.user", message);
        log.debug("发送信令消息给用户: userId={}, messageId={}", userId, message.getMessageId());
    }

    /**
     * 发送文件传输进度消息
     *
     * @param userId   用户ID
     * @param fileId   文件ID
     * @param progress 进度信息
     */
    public void sendFileProgressMessage(Long userId, String fileId, Map<String, Object> progress) {
        Map<String, Object> payload = Map.of(
                "fileId", fileId,
                "progress", progress
        );

        WebSocketMessageDto message = WebSocketMessageDto.builder()
                .messageId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .type("file")
                .channel("user:" + userId)
                .eventType("file")
                .payload(payload)
                .source(getServiceName())
                .build();

        rabbitTemplate.convertAndSend(WEBSOCKET_EXCHANGE, "file.progress", message);
        log.debug("发送文件进度消息: userId={}, fileId={}, messageId={}", userId, fileId, message.getMessageId());
    }

    /**
     * 发送自定义事件到指定用户的个人频道
     * 供业务服务向特定用户推送专属事件（如被踢出房间通知）
     *
     * @param userId    目标用户ID
     * @param eventType 事件类型（如 kicked）
     * @param payload   事件载荷
     */
    public void sendEventToUser(Long userId, String eventType, Map<String, Object> payload) {
        WebSocketMessageDto message = WebSocketMessageDto.builder()
                .messageId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .type("system")
                .channel("user:" + userId)
                .eventType(eventType)
                .payload(payload)
                .source(getServiceName())
                .build();

        rabbitTemplate.convertAndSend(WEBSOCKET_EXCHANGE, "system.user", message);
        log.debug("发送自定义事件给用户: userId={}, eventType={}, messageId={}", userId, eventType, message.getMessageId());
    }

    /**
     * 发送自定义事件到房间频道
     * 供语音等业务服务向房间内所有订阅者推送事件
     *
     * @param roomId    房间ID
     * @param eventType 事件类型（如 call_start / call_end）
     * @param payload   事件载荷
     */
    public void sendEventToRoom(Long roomId, String eventType, Map<String, Object> payload) {
        WebSocketMessageDto message = WebSocketMessageDto.builder()
                .messageId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .type("system")
                .channel("room:" + roomId)
                .eventType(eventType)
                .payload(payload)
                .source(getServiceName())
                .build();

        rabbitTemplate.convertAndSend(WEBSOCKET_EXCHANGE, "system.room", message);
        log.debug("发送语音事件到房间: roomId={}, eventType={}, messageId={}", roomId, eventType, message.getMessageId());
    }

    /**
     * 发送系统通知消息到房间
     *
     * @param roomId       房间ID
     * @param notification 通知内容
     */
    public void sendSystemNotificationToRoom(Long roomId, String notification) {
        Map<String, Object> payload = Map.of(
                "content", notification,
                "timestamp", LocalDateTime.now().toString()
        );

        WebSocketMessageDto message = WebSocketMessageDto.builder()
                .messageId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .type("system")
                .channel("room:" + roomId)
                .eventType("system")
                .payload(payload)
                .source(getServiceName())
                .build();

        rabbitTemplate.convertAndSend(WEBSOCKET_EXCHANGE, "system.room", message);
        log.debug("发送系统通知到房间: roomId={}, messageId={}", roomId, message.getMessageId());
    }

    /**
     * 获取当前服务名称
     */
    private String getServiceName() {
        return System.getProperty("spring.application.name", "unknown-service");
    }

    /**
     * WebSocket消息DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WebSocketMessageDto {
        private String messageId;
        private LocalDateTime timestamp;
        private String type;
        private String channel;
        private String eventType;
        private Map<String, Object> payload;
        private String source;
    }
}
