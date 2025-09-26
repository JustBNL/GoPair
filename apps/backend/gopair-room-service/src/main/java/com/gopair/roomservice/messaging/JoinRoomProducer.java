package com.gopair.roomservice.messaging;

import com.gopair.roomservice.domain.event.JoinRoomRequestedEvent;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JoinRoomProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${mq.room-join.exchange}")
    private String exchange;
    @Value("${mq.room-join.routing-key}")
    private String routingKey;

    public JoinRoomProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public boolean sendRequested(JoinRoomRequestedEvent event) {
        try {
            CorrelationData cd = new CorrelationData(event.getJoinToken() != null ? event.getJoinToken() : UUID.randomUUID().toString());
            rabbitTemplate.convertAndSend(exchange, routingKey, event, cd);
            // 若需要同步等待confirm，可在此处阻塞等待；当前依赖全局confirm回调与重试策略
            return true;
        } catch (Exception e) {
            return false;
        }
    }
} 