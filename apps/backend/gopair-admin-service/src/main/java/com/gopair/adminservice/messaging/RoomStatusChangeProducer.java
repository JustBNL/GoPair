package com.gopair.adminservice.messaging;

import com.gopair.common.constants.SystemConstants;
import com.gopair.common.domain.event.RoomStatusChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 房间状态变更事件生产者，供 admin-service 发布禁用/解禁事件给 room-service。
 *
 * @author gopair
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomStatusChangeProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布禁用房间事件。
     */
    public void sendDisable(Long roomId, Long adminId, String reason) {
        RoomStatusChangeEvent event = new RoomStatusChangeEvent(
                roomId,
                RoomStatusChangeEvent.Action.DISABLE,
                reason,
                adminId,
                System.currentTimeMillis()
        );
        rabbitTemplate.convertAndSend(
                SystemConstants.EXCHANGE_ROOM_STATUS_CHANGE,
                SystemConstants.ROUTING_KEY_ROOM_STATUS_CHANGE,
                event
        );
        log.info("[admin-service][mq] 发布禁用房间事件 roomId={} adminId={}", roomId, adminId);
    }

    /**
     * 发布解禁房间事件。
     */
    public void sendEnable(Long roomId, Long adminId) {
        RoomStatusChangeEvent event = new RoomStatusChangeEvent(
                roomId,
                RoomStatusChangeEvent.Action.ENABLE,
                null,
                adminId,
                System.currentTimeMillis()
        );
        rabbitTemplate.convertAndSend(
                SystemConstants.EXCHANGE_ROOM_STATUS_CHANGE,
                SystemConstants.ROUTING_KEY_ROOM_STATUS_CHANGE,
                event
        );
        log.info("[admin-service][mq] 发布解禁房间事件 roomId={} adminId={}", roomId, adminId);
    }
}
