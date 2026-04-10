package com.gopair.websocketservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.common.constants.MessageConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * RabbitMQ 配置类
 * 配置 WebSocket 服务的消息队列，包括 Exchange、Queue、Binding 及监听器工厂。
 *
 * @author gopair
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RabbitMQConfig {

    private final ObjectMapper objectMapper;

    public static final String WEBSOCKET_EXCHANGE = MessageConstants.WEBSOCKET_EXCHANGE;
    public static final String CHAT_QUEUE = MessageConstants.QUEUE_WEBSOCKET_CHAT;
    public static final String SIGNALING_QUEUE = MessageConstants.QUEUE_WEBSOCKET_SIGNALING;
    public static final String FILE_QUEUE = MessageConstants.QUEUE_WEBSOCKET_FILE;
    public static final String SYSTEM_QUEUE = MessageConstants.QUEUE_WEBSOCKET_SYSTEM;
    public static final String USER_OFFLINE_QUEUE = MessageConstants.QUEUE_USER_OFFLINE;

    /** 聊天/系统/离线队列默认 TTL：5 分钟 */
    private static final int TTL_5_MINUTES = 300000;
    /** 信令队列 TTL：1 分钟（时效性要求高） */
    private static final int TTL_1_MINUTE = 60000;
    /** 文件队列 TTL：10 分钟 */
    private static final int TTL_10_MINUTES = 600000;

    /** 聊天队列最大长度：10 万条 */
    private static final long MAX_LENGTH_CHAT = 100000L;
    /** 信令队列最大长度：5 万条 */
    private static final long MAX_LENGTH_SIGNALING = 50000L;
    /** 文件队列最大长度：1 万条（文件消息较大） */
    private static final long MAX_LENGTH_FILE = 10000L;
    /** 系统队列最大长度：2 万条 */
    private static final long MAX_LENGTH_SYSTEM = 20000L;
    /** 离线队列最大长度：5 万条 */
    private static final long MAX_LENGTH_OFFLINE = 50000L;

    @PostConstruct
    public void init() {
        log.info("[WebSocket服务] RabbitMQ配置初始化完成");
    }

    /**
     * 声明 Topic Exchange
     */
    @Bean
    public TopicExchange websocketExchange() {
        return ExchangeBuilder
                .topicExchange(WEBSOCKET_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 聊天消息队列
     */
    @Bean
    public Queue chatQueue() {
        return QueueBuilder
                .durable(CHAT_QUEUE)
                .ttl(TTL_5_MINUTES)
                .maxLength(MAX_LENGTH_CHAT)
                .build();
    }

    /**
     * 信令消息队列
     */
    @Bean
    public Queue signalingQueue() {
        return QueueBuilder
                .durable(SIGNALING_QUEUE)
                .ttl(TTL_1_MINUTE)
                .maxLength(MAX_LENGTH_SIGNALING)
                .build();
    }

    /**
     * 文件消息队列
     */
    @Bean
    public Queue fileQueue() {
        return QueueBuilder
                .durable(FILE_QUEUE)
                .ttl(TTL_10_MINUTES)
                .maxLength(MAX_LENGTH_FILE)
                .build();
    }

    /**
     * 系统消息队列
     */
    @Bean
    public Queue systemQueue() {
        return QueueBuilder
                .durable(SYSTEM_QUEUE)
                .ttl(TTL_5_MINUTES)
                .maxLength(MAX_LENGTH_SYSTEM)
                .build();
    }

    /**
     * 用户离线队列，供 room-service 消费以更新成员在线状态
     */
    @Bean
    public Queue userOfflineQueue() {
        return QueueBuilder
                .durable(USER_OFFLINE_QUEUE)
                .ttl(TTL_5_MINUTES)
                .maxLength(MAX_LENGTH_OFFLINE)
                .build();
    }

    /**
     * 绑定聊天队列到 Exchange
     */
    @Bean
    public Binding chatBinding() {
        return BindingBuilder
                .bind(chatQueue())
                .to(websocketExchange())
                .with("chat.*");
    }

    /**
     * 绑定信令队列到 Exchange
     */
    @Bean
    public Binding signalingBinding() {
        return BindingBuilder
                .bind(signalingQueue())
                .to(websocketExchange())
                .with("signaling.*");
    }

    /**
     * 绑定文件队列到 Exchange
     */
    @Bean
    public Binding fileBinding() {
        return BindingBuilder
                .bind(fileQueue())
                .to(websocketExchange())
                .with("file.*");
    }

    /**
     * 绑定系统队列到 Exchange
     */
    @Bean
    public Binding systemBinding() {
        return BindingBuilder
                .bind(systemQueue())
                .to(websocketExchange())
                .with("system.*");
    }

    /**
     * 绑定用户离线队列到 Exchange（room-service 消费）
     */
    @Bean
    public Binding userOfflineBinding() {
        return BindingBuilder
                .bind(userOfflineQueue())
                .to(websocketExchange())
                .with(MessageConstants.ROUTING_KEY_SYSTEM_OFFLINE);
    }

    /**
     * 配置消息监听器容器工厂
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        factory.setMessageConverter(converter);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        return factory;
    }
}
