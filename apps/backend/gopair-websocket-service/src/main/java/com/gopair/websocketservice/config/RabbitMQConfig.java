package com.gopair.websocketservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * RabbitMQ配置类
 * 配置WebSocket服务的消息队列
 * 
 * @author gopair
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RabbitMQConfig {

    private final ObjectMapper objectMapper;

    public static final String WEBSOCKET_EXCHANGE = "websocket.topic";
    public static final String CHAT_QUEUE = "websocket.chat";
    public static final String SIGNALING_QUEUE = "websocket.signaling";
    public static final String FILE_QUEUE = "websocket.file";
    public static final String SYSTEM_QUEUE = "websocket.system";
    public static final String USER_OFFLINE_QUEUE = "user.offline.queue";

    @PostConstruct
    public void init() {
        log.info("[WebSocket服务] RabbitMQ配置初始化完成");
    }

    /**
     * 声明Topic Exchange
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
                .ttl(300000) // 5分钟TTL
                .maxLength(100000L) 
                .build();
    }

    /**
     * 信令消息队列
     */
    @Bean
    public Queue signalingQueue() {
        return QueueBuilder
                .durable(SIGNALING_QUEUE)
                .ttl(60000) // 1分钟TTL（信令消息时效性要求高）
                .maxLength(50000L) 
                .build();
    }

    /**
     * 文件消息队列
     */
    @Bean
    public Queue fileQueue() {
        return QueueBuilder
                .durable(FILE_QUEUE)
                .ttl(600000) // 10分钟TTL
                .maxLength(10000L) 
                .build();
    }

    /**
     * 系统消息队列
     */
    @Bean
    public Queue systemQueue() {
        return QueueBuilder
                .durable(SYSTEM_QUEUE)
                .ttl(300000) // 5分钟TTL
                .maxLength(20000L)
                .build();
    }

    /**
     * 用户离线队列，供 room-service 消费以更新成员在线状态
     */
    @Bean
    public Queue userOfflineQueue() {
        return QueueBuilder
                .durable(USER_OFFLINE_QUEUE)
                .ttl(300000) // 5分钟TTL
                .maxLength(50000L)
                .build();
    }

    /**
     * 绑定聊天队列到Exchange
     */
    @Bean
    public Binding chatBinding() {
        return BindingBuilder
                .bind(chatQueue())
                .to(websocketExchange())
                .with("chat.*");
    }

    /**
     * 绑定信令队列到Exchange
     */
    @Bean
    public Binding signalingBinding() {
        return BindingBuilder
                .bind(signalingQueue())
                .to(websocketExchange())
                .with("signaling.*");
    }

    /**
     * 绑定文件队列到Exchange
     */
    @Bean
    public Binding fileBinding() {
        return BindingBuilder
                .bind(fileQueue())
                .to(websocketExchange())
                .with("file.*");
    }

    /**
     * 绑定系统队列到Exchange
     */
    @Bean
    public Binding systemBinding() {
        return BindingBuilder
                .bind(systemQueue())
                .to(websocketExchange())
                .with("system.*");
    }

    /**
     * 绑定用户离线队列到Exchange（room-service 消费）
     */
    @Bean
    public Binding userOfflineBinding() {
        return BindingBuilder
                .bind(userOfflineQueue())
                .to(websocketExchange())
                .with("system.offline");
    }

    /**
     * 配置消息监听器容器工厂
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        // 使用自定义的ObjectMapper来解决序列化/反序列化问题
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        factory.setMessageConverter(converter);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        return factory;
    }
} 