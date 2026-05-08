package com.gopair.roomservice.messaging;

import com.gopair.common.constants.SystemConstants;
import com.gopair.roomservice.domain.event.MemberRemovalEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 成员移除事件发送器，leaveRoom / kickMember / closeRoom 共用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberRemovalProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送成员移除事件。
     *
     * @param event 成员移除事件
     * @return true=发送成功
     */
    public boolean sendRemoval(MemberRemovalEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    SystemConstants.EXCHANGE_ROOM_LEAVE,
                    SystemConstants.ROUTING_KEY_ROOM_LEAVE,
                    event
            );
            log.info("[房间服务] 发送成员移除事件 roomId={} userId={} leaveType={}",
                    event.getRoomId(), event.getUserId(), event.getLeaveType());
            return true;
        } catch (Exception e) {
            log.error("[房间服务] 发送成员移除事件失败 roomId={} userId={}",
                    event.getRoomId(), event.getUserId(), e);
            return false;
        }
    }
}
