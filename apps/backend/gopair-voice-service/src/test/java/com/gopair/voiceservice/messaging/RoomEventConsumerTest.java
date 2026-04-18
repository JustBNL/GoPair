package com.gopair.voiceservice.messaging;

import com.gopair.voiceservice.base.BaseIntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 房间事件消费者集成测试。
 *
 * * [核心策略]
 * - VoiceServiceTestConfig 提供 TracingAmqpConsumerSupport stub，runWithTracing 直接执行 task。
 * - 按需创建模式下 onRoomCreated 仅消费消息（不打日志），不创建通话记录。
 * - 测试验证：消息被正确处理，无异常抛出。
 *
 * @author gopair
 */
@Slf4j
@DisplayName("RoomEventConsumer 房间事件消费者集成测试")
class RoomEventConsumerTest extends BaseIntegrationTest {

    @Autowired
    private RoomEventConsumer roomEventConsumer;

    @Test
    @DisplayName("onRoomCreated → 按需创建模式下消息被正确消费，无异常")
    void onRoomCreated_ShouldConsumeMessageGracefully() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        Message message = new Message("{}".getBytes(), properties);

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "room_created");
        event.put("roomId", 98765L);
        event.put("userId", 999L);

        assertDoesNotThrow(() -> roomEventConsumer.onRoomCreated(event, message));
        log.info("onRoomCreated 消息消费验证通过（按需创建模式，不自动建话）");
    }

    @Test
    @DisplayName("onRoomCreated → eventType 为 null 时仍正常处理（防御性）")
    void onRoomCreated_NullEventType_ShouldHandleGracefully() {
        MessageProperties properties = new MessageProperties();
        Message message = new Message("{}".getBytes(), properties);

        Map<String, Object> event = new HashMap<>();
        event.put("roomId", 98766L);

        assertDoesNotThrow(() -> roomEventConsumer.onRoomCreated(event, message));
        log.info("onRoomCreated（null eventType）验证通过");
    }
}
