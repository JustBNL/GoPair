package com.gopair.messageservice.service;

import com.gopair.common.core.R;
import com.gopair.messageservice.base.BaseIntegrationTest;
import com.gopair.messageservice.config.MockApplicationEventPublisherConfig;
import com.gopair.messageservice.domain.dto.SendMessageDto;
import com.gopair.messageservice.domain.event.MessageSentEvent;
import com.gopair.messageservice.domain.vo.MessageVO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MessageSentEvent 事件发布与监听集成测试
 *
 * * [核心策略]
 *   - 通过 @Import 加载 MockApplicationEventPublisherConfig，提供 mock 的 ApplicationEventPublisher。
 *   - @MockBean 注入该 mock 实例，通过 verify() 校验发布次数。
 *   - 事件发布异常已在 sendMessage 中被 try-catch 包裹，测试覆盖正常发布路径。
 *
 * * [执行链路]
 *   1. 发送消息 → ApplicationEventPublisher.publishEvent 被调用 → verify 校验调用次数。
 *   2. 用户不在房间 → 无 publishEvent 调用。
 */
@Slf4j
@Import(MockApplicationEventPublisherConfig.class)
class MessageSentEventIntegrationTest extends BaseIntegrationTest {

    @MockBean
    private ApplicationEventPublisher applicationEventPublisher;

    private static final Long ROOM_ID = 1L;
    private static final Long USER_A_ID = 100L;
    private static final Long USER_B_ID = 200L;

    @BeforeEach
    void setUpUserProfiles() {
        // 预置用户资料
        jdbcTemplate.update("MERGE INTO app_user (user_id, nickname, avatar) KEY(user_id) VALUES (?, ?, ?)",
                USER_A_ID, "Alice", "http://avatar/alice.png");
        jdbcTemplate.update("MERGE INTO app_user (user_id, nickname, avatar) KEY(user_id) VALUES (?, ?, ?)",
                USER_B_ID, "Bob", "http://avatar/bob.png");
    }

    // ========== 主干：发送消息触发事件发布 ==========

    @Test
    @DisplayName("【主干】发送文本消息 → ApplicationEventPublisher.publishEvent 被调用 1 次")
    void sendMessage_TriggersPublishEvent() {
        // ---- Step 1: 模拟用户在房间内 ----
        mockUserInRoom(ROOM_ID, USER_A_ID, true);

        // ---- Step 2: 发送消息 ----
        log.info("==== [Step 2: 发送消息触发事件发布] ====");
        SendMessageDto dto = new SendMessageDto();
        dto.setRoomId(ROOM_ID);
        dto.setMessageType(1);
        dto.setContent("Event test message");

        MessageVO result = messageService.sendMessage(dto, USER_A_ID);

        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isNotNull();
        log.info("消息发送成功: messageId={}", result.getMessageId());

        // ---- Step 3: 验证 ApplicationEventPublisher.publishEvent 被调用 1 次 ----
        log.info("==== [Step 3: 验证事件发布调用] ====");
        verify(applicationEventPublisher, times(1)).publishEvent(any(MessageSentEvent.class));
        log.info("事件发布验证通过: publishEvent 被调用 1 次");
    }

    // ========== 主干：多条消息多次触发事件 ==========

    @Test
    @DisplayName("【主干+】发送多条消息 → publishEvent 被调用多次（每条消息一次）")
    void multipleMessages_MultipleEventPublishes() {
        // 模拟两个用户在房间内
        mockUserInRoom(ROOM_ID, USER_A_ID, true);
        mockUserInRoom(ROOM_ID, USER_B_ID, true);

        // Alice 发送消息 1
        SendMessageDto dto1 = new SendMessageDto();
        dto1.setRoomId(ROOM_ID);
        dto1.setMessageType(1);
        dto1.setContent("Message 1");
        messageService.sendMessage(dto1, USER_A_ID);

        // Bob 发送消息 2
        SendMessageDto dto2 = new SendMessageDto();
        dto2.setRoomId(ROOM_ID);
        dto2.setMessageType(1);
        dto2.setContent("Message 2");
        messageService.sendMessage(dto2, USER_B_ID);

        // 验证 publishEvent 被调用 2 次
        verify(applicationEventPublisher, times(2)).publishEvent(any(MessageSentEvent.class));
        log.info("多条消息事件发布验证通过: 2 条消息 → 2 次 publishEvent 调用");
    }

    // ========== 分支：不在房间不触发事件 ==========

    @Test
    @DisplayName("【分支】用户不在房间 → 无消息发送，无 publishEvent 调用")
    void userNotInRoom_NoEventPublished() {
        log.info("==== [分支测试: 用户不在房间，无事件发布] ====");
        SendMessageDto dto = new SendMessageDto();
        dto.setRoomId(ROOM_ID);
        dto.setMessageType(1);
        dto.setContent("Should be blocked");

        Assertions.assertThrows(Exception.class, () -> messageService.sendMessage(dto, USER_A_ID));

        // 验证无事件发布
        verify(applicationEventPublisher, never()).publishEvent(any(MessageSentEvent.class));
        log.info("事件未发布验证通过: 消息被拦截，无 publishEvent 调用");
    }
}
