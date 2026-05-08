package com.gopair.roomservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.gopair.common.constants.SystemConstants.*;

/**
 * Room 服务 RabbitMQ 配置
 *
 * * [职责范围]
 * - 声明 room.join/leave 体系的 Exchange、Queue、Binding（含 DLX/DLQ）
 * - 通过 @Value 注入 TTL 等动态参数
 *
 * @author gopair
 */
@Configuration
public class RabbitConfig {

    @Value("${gopair.mq.room-join-ttl:300000}")
    private int roomJoinTtl;

    @Value("${gopair.mq.room-leave-ttl:300000}")
    private int roomLeaveTtl;

    // ==================== Join resources ====================

    @Bean
    public Exchange roomJoinExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_ROOM_JOIN).durable(true).build();
    }

    @Bean
    public Queue roomJoinQueue() {
        return QueueBuilder.durable(QUEUE_ROOM_JOIN)
                .ttl(roomJoinTtl)
                .withArgument("x-dead-letter-exchange", DL_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DL_ROOM_JOIN)
                .build();
    }

    @Bean
    public Binding roomJoinBinding() {
        return BindingBuilder.bind(roomJoinQueue()).to(roomJoinExchange()).with(ROUTING_KEY_ROOM_JOIN).noargs();
    }

    // ==================== Leave resources ====================

    @Bean
    public Exchange roomLeaveExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_ROOM_LEAVE).durable(true).build();
    }

    @Bean
    public Queue roomLeaveQueue() {
        return QueueBuilder.durable(QUEUE_ROOM_LEAVE)
                .ttl(roomLeaveTtl)
                .build();
    }

    @Bean
    public Binding roomLeaveBinding() {
        return BindingBuilder.bind(roomLeaveQueue()).to(roomLeaveExchange()).with(ROUTING_KEY_ROOM_LEAVE).noargs();
    }

    // ==================== Room Status Change resources ====================

    @Bean
    public Queue roomStatusChangeQueue() {
        return QueueBuilder.durable(QUEUE_ROOM_STATUS_CHANGE).build();
    }

    @Bean
    public Binding roomStatusChangeBinding() {
        TopicExchange statusExchange = ExchangeBuilder.topicExchange(EXCHANGE_ROOM_STATUS_CHANGE).durable(true).build();
        return BindingBuilder.bind(roomStatusChangeQueue()).to(statusExchange).with(ROUTING_KEY_ROOM_STATUS_CHANGE);
    }

    // ==================== Listener factory ====================

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                              MessageConverter jackson2JsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jackson2JsonMessageConverter);
        return factory;
    }
}
