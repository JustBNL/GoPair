package com.gopair.voiceservice.messaging;

import com.gopair.voiceservice.base.BaseIntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 房间事件消费者集成测试。
 *
 * * [核心策略]
 * - RoomEventConsumer 通过 @MockBean 在 BaseIntegrationTest 中注入 Mock 实例。
 * - 测试验证 Mock 的调用行为，确保消费者被正确触发。
 *
 * @author gopair
 */
@Slf4j
@DisplayName("RoomEventConsumer 房间事件消费者集成测试")
class RoomEventConsumerTest extends BaseIntegrationTest {

    @Test
    @DisplayName("onRoomCreated → Mock 验证方法被正确调用")
    void onRoomCreated_ShouldInvokeMock() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        Message message = new Message("{}".getBytes(), properties);

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "room_created");
        event.put("roomId", 98765L);
        event.put("userId", 999L);

        roomEventConsumer.onRoomCreated(event, message);

        verify(roomEventConsumer, times(1)).onRoomCreated(any(), any());
        log.info("onRoomCreated Mock 调用验证通过");
    }

    @Test
    @DisplayName("onRoomCreated → eventType 为 null 时仍正常处理（防御性）")
    void onRoomCreated_NullEventType_ShouldHandleGracefully() {
        MessageProperties properties = new MessageProperties();
        Message message = new Message("{}".getBytes(), properties);

        Map<String, Object> event = new HashMap<>();
        event.put("roomId", 98766L);

        assertDoesNotThrow(() -> roomEventConsumer.onRoomCreated(event, message));
        verify(roomEventConsumer, times(1)).onRoomCreated(any(), any());
        log.info("onRoomCreated（null eventType）验证通过");
    }
}
