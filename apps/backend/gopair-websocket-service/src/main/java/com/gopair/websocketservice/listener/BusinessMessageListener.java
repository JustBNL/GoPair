package com.gopair.websocketservice.listener;

import com.gopair.websocketservice.config.RabbitMQConfig;
import com.gopair.websocketservice.protocol.MessageType;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import com.gopair.websocketservice.service.ChannelMessageRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * - 转换消息格式
 * - 分发消息到WebSocket连接
 * - 处理不同类型的业务消息
 * 
 * 架构设计：
 * - 使用RabbitMQ作为消息中间件
 * - 支持多个消息队列（聊天、信令、文件、系统）
 * - 与ConnectionManagerService协作进行消息分发
 * 
 * 支持的消息类型：
 * 1. 聊天消息：房间聊天、私聊等
 * 2. 信令消息：WebRTC信令、控制消息等
 * 3. 文件消息：文件上传、下载通知等
 * 4. 系统消息：系统公告、维护通知等
 * 
 * 消息处理流程：
 * 1. 从RabbitMQ队列接收消息
 * 2. 转换为UnifiedWebSocketMessage格式
 * 3. 归一化时间字段格式
 * 4. 调用ConnectionManagerService进行消息分发
 * 
 * 性能优化：
 * - 使用批量监听器提升吞吐量
 * - 优化消息转换逻辑
 * - 减少不必要的对象创建
 * 
 * @author gopair
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessMessageListener {

    private final ChannelMessageRouter channelMessageRouter;

    /**
     * 监听聊天消息队列
     * 
     * 队列：websocket.chat
     * 路由键：chat.*
     * 
     * @param messageDto 消息数据传输对象
     */
    @RabbitListener(queues = RabbitMQConfig.CHAT_QUEUE)
    public void handleChatMessage(WebSocketMessageDto messageDto) {
        log.debug("[消息监听] 收到聊天消息: messageId={}, channel={}, eventType={}", 
                 messageDto.getMessageId(), messageDto.getChannel(), messageDto.getEventType());
        
        // 转换为UnifiedWebSocketMessage
        UnifiedWebSocketMessage message = convertToUnifiedMessage(messageDto);
        
        // 使用频道路由处理消息
        channelMessageRouter.processChannelMessage(message);
    }

    /**
     * 监听信令消息队列
     */
    @RabbitListener(queues = RabbitMQConfig.SIGNALING_QUEUE)
    public void handleSignalingMessage(WebSocketMessageDto messageDto) {
        log.debug("[消息监听] 收到信令消息: messageId={}, channel={}, eventType={}", 
                 messageDto.getMessageId(), messageDto.getChannel(), messageDto.getEventType());
        
        // 转换为UnifiedWebSocketMessage
        UnifiedWebSocketMessage message = convertToUnifiedMessage(messageDto);
        
        // 使用频道路由处理消息
        channelMessageRouter.processChannelMessage(message);
    }

    /**
     * 监听文件消息队列
     */
    @RabbitListener(queues = RabbitMQConfig.FILE_QUEUE)
    public void handleFileMessage(WebSocketMessageDto messageDto) {
        log.debug("[消息监听] 收到文件消息: messageId={}, channel={}, eventType={}", 
                 messageDto.getMessageId(), messageDto.getChannel(), messageDto.getEventType());
        
        // 转换为UnifiedWebSocketMessage
        UnifiedWebSocketMessage message = convertToUnifiedMessage(messageDto);
        
        // 使用频道路由处理消息
        channelMessageRouter.processChannelMessage(message);
    }

    /**
     * 监听系统消息队列
     */
    @RabbitListener(queues = RabbitMQConfig.SYSTEM_QUEUE)
    public void handleSystemMessage(WebSocketMessageDto messageDto) {
        log.debug("[消息监听] 收到系统消息: messageId={}, channel={}, eventType={}", 
                 messageDto.getMessageId(), messageDto.getChannel(), messageDto.getEventType());
        
        // 转换为UnifiedWebSocketMessage
        UnifiedWebSocketMessage message = convertToUnifiedMessage(messageDto);
        
        // 使用频道路由处理消息
        channelMessageRouter.processChannelMessage(message);
    }
    
    /**
     * 将WebSocketMessageDto转换为UnifiedWebSocketMessage
     */
    @SuppressWarnings("unchecked")
    private UnifiedWebSocketMessage convertToUnifiedMessage(WebSocketMessageDto dto) {
        UnifiedWebSocketMessage message = new UnifiedWebSocketMessage();
        message.setMessageId(dto.getMessageId());
        
        // 处理时间戳 - 如果为null则使用当前时间
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
        
        // 转换消息类型
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
                    int year = ((Number) list.get(0)).intValue();
                    int month = ((Number) list.get(1)).intValue();
                    int day = ((Number) list.get(2)).intValue();
                    int hour = list.size() > 3 ? ((Number) list.get(3)).intValue() : 0;
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
     * 用于接收来自RabbitMQ的消息
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