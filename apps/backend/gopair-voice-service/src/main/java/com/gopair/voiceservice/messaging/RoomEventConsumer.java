package com.gopair.voiceservice.messaging;

import com.gopair.common.util.TracingAmqpConsumerSupport;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 房间事件消费者
 * 已切换为按需创建模式，房间创建时不再自动创建语音通话。
 *
 * @author gopair
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomEventConsumer {

    private final TracingAmqpConsumerSupport tracingAmqpConsumerSupport;

    /**
     * 监听房间创建事件。
     * 按需创建模式下，通话由第一个点击「加入」的用户触发创建，此处仅消费消息避免队列积压。
     *
     * @param message    消息内容
     * @param rawMessage 原始 AMQP 消息（用于提取追踪消息头）
     */
    @RabbitListener(queues = "${mq.voice.room-created.queue:voice.room.created.queue}")
    public void onRoomCreated(Map<String, Object> message, Message rawMessage) {
        tracingAmqpConsumerSupport.runWithTracing(rawMessage, () -> {
            log.info("[语音] 收到 room_created 事件，按需创建模式下忽略自动建话: eventType={}",
                    message.get("eventType"));
        });
    }
}
