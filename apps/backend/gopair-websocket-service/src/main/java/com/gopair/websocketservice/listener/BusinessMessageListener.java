package com.gopair.websocketservice.listener;

import com.gopair.websocketservice.config.RabbitMQConfig;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import com.gopair.websocketservice.service.ConnectionManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 业务消息监听器
 * 监听来自业务服务的RabbitMQ消息并分发到WebSocket连接
 * 
 * @author gopair
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessMessageListener {

    private final ConnectionManagerService connectionManager;

    /**
     * 监听聊天消息队列
     */
    @RabbitListener(queues = RabbitMQConfig.CHAT_QUEUE)
    public void handleChatMessage(UnifiedWebSocketMessage message) {
                    log.debug("[消息监听] 收到聊天消息: messageId={}, channel={}, eventType={}", 
                 message.getMessageId(), message.getChannel(), message.getEventType());
        
        // 使用频道路由处理消息
        connectionManager.processChannelMessage(message);
    }

    /**
     * 监听信令消息队列
     */
    @RabbitListener(queues = RabbitMQConfig.SIGNALING_QUEUE)
    public void handleSignalingMessage(UnifiedWebSocketMessage message) {
                    log.debug("[消息监听] 收到信令消息: messageId={}, channel={}, eventType={}", 
                 message.getMessageId(), message.getChannel(), message.getEventType());
        
        // 使用频道路由处理消息
        connectionManager.processChannelMessage(message);
    }

    /**
     * 监听文件消息队列
     */
    @RabbitListener(queues = RabbitMQConfig.FILE_QUEUE)
    public void handleFileMessage(UnifiedWebSocketMessage message) {
                    log.debug("[消息监听] 收到文件消息: messageId={}, channel={}, eventType={}", 
                 message.getMessageId(), message.getChannel(), message.getEventType());
        
        // 使用频道路由处理消息
        connectionManager.processChannelMessage(message);
    }

    /**
     * 监听系统消息队列
     */
    @RabbitListener(queues = RabbitMQConfig.SYSTEM_QUEUE)
    public void handleSystemMessage(UnifiedWebSocketMessage message) {
                    log.debug("[消息监听] 收到系统消息: messageId={}, channel={}, eventType={}", 
                 message.getMessageId(), message.getChannel(), message.getEventType());
        
        // 使用频道路由处理消息
        connectionManager.processChannelMessage(message);
    }
} 