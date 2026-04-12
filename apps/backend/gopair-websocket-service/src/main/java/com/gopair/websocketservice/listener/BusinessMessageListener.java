package com.gopair.websocketservice.listener;

import com.gopair.common.constants.SystemConstants;
import com.gopair.common.util.TracingAmqpConsumerSupport;
import com.gopair.websocketservice.protocol.MessageType;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import com.gopair.websocketservice.service.ChannelMessageRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 业务消息监听器
 *
 * 职责：
 * - 监听来自业务服务的RabbitMQ消息
 * - 恢复追踪上下文（traceId/userId/nickname 写入 MDC）
 * - 转换消息格式
 * - 分发消息到WebSocket连接
 * - 处理不同类型的业务消息
 *
 * 追踪增强：
 * 每个消息处理方法在执行前通过 TracingAmqpConsumerSupport 从 AMQP 消息头
 * 恢复 traceId/userId/nickname 到 MDC，实现跨 MQ 的全链路日志追踪。
 *
 * @author gopair
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessMessageListener {

    private final ChannelMessageRouter channelMessageRouter;
    private final TracingAmqpConsumerSupport tracingAmqpConsumerSupport;

    /**
     * 监听聊天消息队列
     *
     * @param messageDto  反序列化后的消息 DTO
     * @param rawMessage  原始 AMQP 消息（用于提取追踪消息头）
     */
    @RabbitListener(queues = SystemConstants.QUEUE_WEBSOCKET_CHAT)
    public void handleChatMessage(WebSocketMessageDto messageDto, Message rawMessage) {
        tracingAmqpConsumerSupport.runWithTracing(rawMessage, () -> {
            log.debug("[消息监听] 收到聊天消息: messageId={}, channel={}, eventType={}",
                    messageDto.getMessageId(), messageDto.getChannel(), messageDto.getEventType());
            channelMessageRouter.processChannelMessage(convertToUnifiedMessage(messageDto));
        });
    }

    /**
     * 监听信令消息队列
     */
    @RabbitListener(queues = SystemConstants.QUEUE_WEBSOCKET_SIGNALING)
    public void handleSignalingMessage(WebSocketMessageDto messageDto, Message rawMessage) {
        tracingAmqpConsumerSupport.runWithTracing(rawMessage, () -> {
            log.debug("[消息监听] 收到信令消息: messageId={}, channel={}, eventType={}",
                    messageDto.getMessageId(), messageDto.getChannel(), messageDto.getEventType());
            channelMessageRouter.processChannelMessage(convertToUnifiedMessage(messageDto));
        });
    }

    /**
     * 监听文件消息队列
     */
    @RabbitListener(queues = SystemConstants.QUEUE_WEBSOCKET_FILE)
    public void handleFileMessage(WebSocketMessageDto messageDto, Message rawMessage) {
        tracingAmqpConsumerSupport.runWithTracing(rawMessage, () -> {
            log.debug("[消息监听] 收到文件消息: messageId={}, channel={}, eventType={}",
                    messageDto.getMessageId(), messageDto.getChannel(), messageDto.getEventType());
            channelMessageRouter.processChannelMessage(convertToUnifiedMessage(messageDto));
        });
    }

    /**
     * 监听系统消息队列
     */
    @RabbitListener(queues = SystemConstants.QUEUE_WEBSOCKET_SYSTEM)
    public void handleSystemMessage(WebSocketMessageDto messageDto, Message rawMessage) {
        tracingAmqpConsumerSupport.runWithTracing(rawMessage, () -> {
            log.debug("[消息监听] 收到系统消息: messageId={}, channel={}, eventType={}",
                    messageDto.getMessageId(), messageDto.getChannel(), messageDto.getEventType());
            channelMessageRouter.processChannelMessage(convertToUnifiedMessage(messageDto));
        });
    }

    /**
     * 将WebSocketMessageDto转换为UnifiedWebSocketMessage
     */
    @SuppressWarnings("unchecked")
    private UnifiedWebSocketMessage convertToUnifiedMessage(WebSocketMessageDto dto) {
        UnifiedWebSocketMessage message = new UnifiedWebSocketMessage();
        message.setMessageId(dto.getMessageId());

        LocalDateTime timestamp = dto.getTimestamp();
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        message.setTimestamp(timestamp);

        message.setChannel(dto.getChannel());
        message.setEventType(dto.getEventType());

        Map<String, Object> payload = dto.getPayload();
        if (payload != null) {
            normalizeTimeFields(payload);
        }
        message.setPayload(payload);
        message.setSource(dto.getSource());

        try {
            message.setType(MessageType.fromValue(dto.getType()));
        } catch (IllegalArgumentException e) {
            log.warn("[消息监听] 未知的消息类型: {}, 使用默认类型CHAT", dto.getType());
            message.setType(MessageType.CHAT);
        }

        return message;
    }

    /**
     * 归一化payload内的时间字段：将[yyyy,MM,dd,HH,mm,ss]数组转为ISO字符串
     */
    @SuppressWarnings("unchecked")
    private void normalizeTimeFields(Map<String, Object> map) {
        if (map == null) return;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value == null) continue;
            if (value instanceof Map) {
                normalizeTimeFields((Map<String, Object>) value);
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                if (list.size() >= 3 && list.stream().allMatch(it -> it instanceof Number)) {
                    int year   = ((Number) list.get(0)).intValue();
                    int month  = ((Number) list.get(1)).intValue();
                    int day    = ((Number) list.get(2)).intValue();
                    int hour   = list.size() > 3 ? ((Number) list.get(3)).intValue() : 0;
                    int minute = list.size() > 4 ? ((Number) list.get(4)).intValue() : 0;
                    int second = list.size() > 5 ? ((Number) list.get(5)).intValue() : 0;
                    try {
                        LocalDateTime ldt = LocalDateTime.of(year, month, day, hour, minute, second);
                        entry.setValue(ldt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    } catch (Exception ignore) {
                        // 保持原值
                    }
                }
            }
        }
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
