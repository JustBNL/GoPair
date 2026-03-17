package com.gopair.roomservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Value("${mq.room-join.exchange}")
    private String roomJoinExchange;
    @Value("${mq.room-join.routing-key}")
    private String roomJoinRoutingKey;
    @Value("${mq.room-join.queue}")
    private String roomJoinQueue;
    @Value("${mq.room-join.dlx}")
    private String roomJoinDlx;
    @Value("${mq.room-join.dlq}")
    private String roomJoinDlq;

    @Value("${mq.room-leave.exchange:room.leave.exchange}")
    private String roomLeaveExchangeName;
    @Value("${mq.room-leave.routing-key:room.leave.requested}")
    private String roomLeaveRoutingKey;
    @Value("${mq.room-leave.queue:room.leave.queue}")
    private String roomLeaveQueueName;

    @Bean
    public Exchange roomJoinExchange() {
        return ExchangeBuilder.topicExchange(roomJoinExchange).durable(true).build();
    }

    @Bean
    public Exchange roomJoinDlx() {
        return ExchangeBuilder.topicExchange(roomJoinDlx).durable(true).build();
    }

    @Bean
    public Queue roomJoinQueue() {
        return QueueBuilder.durable(roomJoinQueue)
                .withArgument("x-dead-letter-exchange", roomJoinDlx)
                .withArgument("x-dead-letter-routing-key", roomJoinRoutingKey + ".dlq")
                .build();
    }

    @Bean
    public Queue roomJoinDlq() {
        return QueueBuilder.durable(roomJoinDlq).build();
    }

    @Bean
    public Binding roomJoinBinding() {
        return BindingBuilder.bind(roomJoinQueue()).to(roomJoinExchange()).with(roomJoinRoutingKey).noargs();
    }

    @Bean
    public Binding roomJoinDlqBinding() {
        return BindingBuilder.bind(roomJoinDlq()).to(roomJoinDlx()).with(roomJoinRoutingKey + ".dlq").noargs();
    }

    // Leave resources
    @Bean
    public Exchange roomLeaveExchange() {
        return ExchangeBuilder.topicExchange(roomLeaveExchangeName).durable(true).build();
    }

    @Bean
    public Queue roomLeaveQueue() {
        return QueueBuilder.durable(roomLeaveQueueName).build();
    }

    @Bean
    public Binding roomLeaveBinding() {
        return BindingBuilder.bind(roomLeaveQueue()).to(roomLeaveExchange()).with(roomLeaveRoutingKey).noargs();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                               MessageConverter jackson2JsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jackson2JsonMessageConverter);
        return factory;
    }
}
