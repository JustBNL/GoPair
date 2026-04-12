package com.gopair.roomservice.messaging;

import com.gopair.common.constants.SystemConstants;
import com.gopair.framework.logging.annotation.LogRecord;
import com.gopair.roomservice.domain.event.JoinRoomRequestedEvent;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JoinRoomProducer {

    private final RabbitTemplate rabbitTemplate;

    public JoinRoomProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @LogRecord(operation = "发送加入房间事件", module = "消息发送", includeResult = true)
    public boolean sendRequested(JoinRoomRequestedEvent event) {
        try {
            CorrelationData cd = new CorrelationData(event.getJoinToken() != null ? event.getJoinToken() : UUID.randomUUID().toString());
            rabbitTemplate.convertAndSend(
                    SystemConstants.EXCHANGE_ROOM_JOIN,
                    SystemConstants.ROUTING_KEY_ROOM_JOIN,
                    event, cd);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
