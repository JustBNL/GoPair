package com.gopair.roomservice.messaging;

import com.gopair.framework.logging.annotation.LogRecord;
import com.gopair.roomservice.domain.event.LeaveRoomRequestedEvent;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LeaveRoomProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${mq.room-leave.exchange}")
    private String exchange;
    @Value("${mq.room-leave.routing-key}")
    private String routingKey;

    public LeaveRoomProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @LogRecord(operation = "发送离开房间事件", module = "消息发送", includeResult = true)
    public boolean sendRequested(LeaveRoomRequestedEvent event) {
        try {
            CorrelationData cd = new CorrelationData(event.getCorrelationId() != null ? event.getCorrelationId() : UUID.randomUUID().toString());
            rabbitTemplate.convertAndSend(exchange, routingKey, event, cd);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
} 