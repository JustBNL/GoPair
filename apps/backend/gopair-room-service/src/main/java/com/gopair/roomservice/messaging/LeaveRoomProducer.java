package com.gopair.roomservice.messaging;

import com.gopair.common.constants.SystemConstants;
import com.gopair.framework.logging.annotation.LogRecord;
import com.gopair.roomservice.domain.event.LeaveRoomRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class LeaveRoomProducer {

    private final RabbitTemplate rabbitTemplate;

    public LeaveRoomProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @LogRecord(operation = "发送离开房间事件", module = "消息发送", includeResult = true)
    public boolean sendRequested(LeaveRoomRequestedEvent event) {
        try {
            CorrelationData cd = new CorrelationData(event.getCorrelationId() != null ? event.getCorrelationId() : UUID.randomUUID().toString());
            rabbitTemplate.convertAndSend(
                    SystemConstants.EXCHANGE_ROOM_LEAVE,
                    SystemConstants.ROUTING_KEY_ROOM_LEAVE,
                    event, cd);
            return true;
        } catch (Exception e) {
            log.warn("[房间服务] RabbitMQ 发送离开事件失败 roomId={} userId={} correlationId={} 错误={}",
                    event.getRoomId(), event.getUserId(), event.getCorrelationId(), e.getMessage());
            return false;
        }
    }
}
