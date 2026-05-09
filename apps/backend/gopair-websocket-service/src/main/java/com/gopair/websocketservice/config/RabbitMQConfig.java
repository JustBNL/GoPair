package com.gopair.websocketservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.common.constants.SystemConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * RabbitMQ 配置类
 *
 * * [职责范围]
 * - 声明 ws-service 专用的 5 个共享队列（chat/signaling/file/system/offline）
 * - websocket.topic Exchange 由 gopair-common/RabbitMQInfrastructureConfig 统一声明，此处注入使用同一个 Bean
 * - 消息监听器线程池工厂（个性化并发参数）
 *
 * @author gopair
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RabbitMQConfig {

    private final ObjectMapper objectMapper;

    @Value("${gopair.mq.websocket-chat-ttl:300000}")
    private int chatTtl;
    @Value("${gopair.mq.websocket-signaling-ttl:60000}")
    private int signalingTtl;
    @Value("${gopair.mq.websocket-file-ttl:600000}")
    private int fileTtl;
    @Value("${gopair.mq.websocket-system-ttl:300000}")
    private int systemTtl;
    @Value("${gopair.mq.websocket-offline-ttl:300000}")
    private int offlineTtl;

    @Value("${gopair.mq.websocket-chat-max-length:100000}")
    private long chatMaxLength;
    @Value("${gopair.mq.websocket-signaling-max-length:50000}")
    private long signalingMaxLength;
    @Value("${gopair.mq.websocket-file-max-length:10000}")
    private long fileMaxLength;
    @Value("${gopair.mq.websocket-system-max-length:20000}")
    private long systemMaxLength;
    @Value("${gopair.mq.websocket-offline-max-length:50000}")
    private long offlineMaxLength;

    @PostConstruct
    public void init() {
        log.info("[WebSocket服务] RabbitMQ配置初始化完成");
    }

    // ==================== Shared queues ====================

    @Bean
    public Queue websocketChatQueue() {
        return QueueBuilder.durable(SystemConstants.QUEUE_WEBSOCKET_CHAT)
                .ttl(chatTtl)
                .maxLength(chatMaxLength)
                .withArgument("x-dead-letter-exchange", SystemConstants.DL_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SystemConstants.ROUTING_KEY_DL_WS_CHAT)
                .build();
    }

    @Bean
    public Queue websocketSignalingQueue() {
        return QueueBuilder.durable(SystemConstants.QUEUE_WEBSOCKET_SIGNALING)
                .ttl(signalingTtl)
                .maxLength(signalingMaxLength)
                .withArgument("x-dead-letter-exchange", SystemConstants.DL_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SystemConstants.ROUTING_KEY_DL_WS_SIGNALING)
                .build();
    }

    @Bean
    public Queue websocketFileQueue() {
        return QueueBuilder.durable(SystemConstants.QUEUE_WEBSOCKET_FILE)
                .ttl(fileTtl)
                .maxLength(fileMaxLength)
                .withArgument("x-dead-letter-exchange", SystemConstants.DL_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SystemConstants.ROUTING_KEY_DL_WS_FILE)
                .build();
    }

    @Bean
    public Queue websocketSystemQueue() {
        return QueueBuilder.durable(SystemConstants.QUEUE_WEBSOCKET_SYSTEM)
                .ttl(systemTtl)
                .maxLength(systemMaxLength)
                .withArgument("x-dead-letter-exchange", SystemConstants.DL_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SystemConstants.ROUTING_KEY_DL_WS_SYSTEM)
                .build();
    }

    @Bean
    public Queue userOfflineQueue() {
        return QueueBuilder.durable(SystemConstants.QUEUE_USER_OFFLINE)
                .ttl(offlineTtl)
                .maxLength(offlineMaxLength)
                .withArgument("x-dead-letter-exchange", SystemConstants.DL_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SystemConstants.ROUTING_KEY_DL_WS_OFFLINE)
                .build();
    }

    // ==================== Bindings ====================

    @Bean
    public Binding chatBinding(TopicExchange websocketTopicExchange) {
        return BindingBuilder.bind(websocketChatQueue())
                .to(websocketTopicExchange)
                .with("chat.*");
    }

    @Bean
    public Binding signalingBinding(TopicExchange websocketTopicExchange) {
        return BindingBuilder.bind(websocketSignalingQueue())
                .to(websocketTopicExchange)
                .with("signaling.*");
    }

    @Bean
    public Binding signalingRoomBinding(TopicExchange websocketTopicExchange) {
        return BindingBuilder.bind(websocketSignalingQueue())
                .to(websocketTopicExchange)
                .with(SystemConstants.ROUTING_KEY_SIGNALING_ROOM);
    }

    @Bean
    public Binding fileBinding(TopicExchange websocketTopicExchange) {
        return BindingBuilder.bind(websocketFileQueue())
                .to(websocketTopicExchange)
                .with("file.*");
    }

    @Bean
    public Binding systemBinding(TopicExchange websocketTopicExchange) {
        return BindingBuilder.bind(websocketSystemQueue())
                .to(websocketTopicExchange)
                .with("system.*");
    }

    @Bean
    public Binding userOfflineBinding(TopicExchange websocketTopicExchange) {
        return BindingBuilder.bind(userOfflineQueue())
                .to(websocketTopicExchange)
                .with(SystemConstants.ROUTING_KEY_SYSTEM_OFFLINE);
    }

    // ==================== Listener factory ====================

    /**
     * 消息监听器容器工厂
     *
     * * [个性化参数]
     * - concurrentConsumers: 3（最小并发消费线程数）
     * - maxConcurrentConsumers: 10（最大并发消费线程数）
     * - 消费者 prefetch/concurrency 在 gopair-shared.yml 中通过 spring.rabbitmq.listener.simple 统一配置
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
