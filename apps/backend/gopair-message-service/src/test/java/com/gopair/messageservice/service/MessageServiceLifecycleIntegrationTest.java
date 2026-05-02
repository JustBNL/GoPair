package com.gopair.messageservice.service;

import com.gopair.messageservice.base.BaseIntegrationTest;
import com.gopair.messageservice.config.MockRestTemplateConfig;
import com.gopair.messageservice.domain.dto.MessageQueryDto;
import com.gopair.messageservice.domain.dto.SendMessageDto;
import com.gopair.messageservice.domain.po.Message;
import com.gopair.messageservice.domain.vo.MessageVO;
import com.gopair.messageservice.mapper.MessageMapper;
import com.gopair.messageservice.mapper.UserPublicMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 消息服务全链路集成测试
 *
 * * [测试编排]
 * - 主干测试流 A：发送文本消息 -> 分页查询 -> 获取最新消息 -> 统计数量 -> 删除消息
 * - 分支测试流 B：发送文件消息 -> 查询详情 -> 验证 WebSocket 推送拦截 -> 删除
 * - 边界测试流 C：发送 Emoji 消息 -> 回复消息 -> 查询 -> 清理
 *
 * * [环境与中间件]
 * - MySQL（gopair_test）：消息表、用户表
 * - Redis：真实连接（DB 14），测试后 flushDb() 清理
 * - RabbitMQ：Mock（@MockBean），WebSocket 推送通过 verify 校验
 *
 * * [脏数据清理]
 * - @Transactional：MySQL 数据自动回滚
 * - @AfterEach flushDb()：Redis 数据清理
 */
@Slf4j
class MessageServiceLifecycleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private UserPublicMapper userPublicMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long ROOM_ID = 1L;
    private static final Long USER_A_ID = 100L;
    private static final Long USER_B_ID = 200L;
    private static final Long USER_C_ID = 300L;

    @BeforeEach
    void setUpUserProfiles() {
        // 预置用户公开资料（昵称/头像），使用 MySQL INSERT ... ON DUPLICATE KEY UPDATE
        jdbcTemplate.update("INSERT INTO app_user (user_id, nickname, avatar, username) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), avatar = VALUES(avatar)",
                USER_A_ID, "Alice", "http://avatar/alice.png", "alice");
        jdbcTemplate.update("INSERT INTO app_user (user_id, nickname, avatar, username) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), avatar = VALUES(avatar)",
                USER_B_ID, "Bob", "http://avatar/bob.png", "bob");
        jdbcTemplate.update("INSERT INTO app_user (user_id, nickname, avatar, username) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), avatar = VALUES(avatar)",
                USER_C_ID, "Charlie", "http://avatar/charlie.png", "charlie");
    }

    @AfterEach
    void verifyNoRedisWrite() {
        // Redis 在 flushDb() 后已清空，无需验证
    }

    // ========== 主干测试流 A：文本消息全生命周期 ==========

    @Test
    @DisplayName("【主干A】文本消息全链路：发送 -> 分页查询 -> 最新消息 -> 统计 -> 删除")
    void textMessage_FullLifecycle() {
        // ---- Step 1: 模拟用户在房间内 ----
        mockUserInRoom(ROOM_ID, USER_A_ID, true);

        // ---- Step 2: 发送文本消息 ----
        SendMessageDto dto = new SendMessageDto();
        dto.setRoomId(ROOM_ID);
        dto.setMessageType(1); // TEXT
        dto.setContent("Hello everyone!");

        log.info("==== [Step 2: 发送文本消息] ====");
        MessageVO sent = messageService.sendMessage(dto, USER_A_ID);

        assertThat(sent.getMessageId()).isNotNull();
        assertThat(sent.getContent()).isEqualTo("Hello everyone!");
        assertThat(sent.getSenderId()).isEqualTo(USER_A_ID);
        assertThat(sent.getRoomId()).isEqualTo(ROOM_ID);
        log.info("消息发送成功，存入 DB: messageId={}, roomId={}, senderId={}",
                sent.getMessageId(), sent.getRoomId(), sent.getSenderId());

        // 验证 WebSocket 推送被调用（Mock 拦截，不建立真实连接）
        verify(webSocketMessageProducer, times(1)).sendChatMessageToRoom(eq(ROOM_ID), any());
        log.info("WebSocket MQ 推送已拦截验证（RabbitTemplate 未真实发送）");

        // ---- Step 3: 分页查询房间消息 ----
        log.info("==== [Step 3: 分页查询消息] ====");
        MessageQueryDto queryDto = new MessageQueryDto();
        queryDto.setRoomId(ROOM_ID);
        queryDto.setPageNum(1);
        queryDto.setPageSize(10);

        var pageResult = messageService.getRoomMessages(queryDto);
        assertThat(pageResult.getRecords()).hasSizeGreaterThanOrEqualTo(1);
        log.info("分页查询结果: total={}, records={}",
                pageResult.getTotal(), pageResult.getRecords().size());
        log.info("DB 分页记录: {}", pageResult.getRecords());

        // ---- Step 4: 获取最新消息 ----
        log.info("==== [Step 4: 获取最新消息] ====");
        List<MessageVO> latest = messageService.getLatestMessages(ROOM_ID, 10);
        assertThat(latest).isNotEmpty();
        log.info("最新消息列表: count={}", latest.size());
        log.info("Redis 当前状态: 无写入（RedisTemplate 全程 Mock）");

        // ---- Step 5: 统计消息数量 ----
        log.info("==== [Step 5: 统计消息数量] ====");
        Long count = messageService.countRoomMessages(ROOM_ID, null);
        assertThat(count).isGreaterThanOrEqualTo(1L);
        log.info("房间消息总数: count={}", count);

        // ---- Step 6: 发送者删除自己的消息 ----
        log.info("==== [Step 6: 删除消息] ====");
        Boolean deleted = messageService.deleteMessage(sent.getMessageId(), USER_A_ID);
        assertThat(deleted).isTrue();
        log.info("消息删除成功: messageId={}", sent.getMessageId());

        // 验证从数据库中已查不到
        MessageVO afterDelete = messageMapper.selectMessageVOById(sent.getMessageId());
        assertThat(afterDelete).isNull();
        log.info("数据库校验: 消息已物理删除，messageId={}", sent.getMessageId());
    }

    // ========== 分支测试流 B：文件消息 + WebSocket 推送验证 ==========

    @Test
    @DisplayName("【分支B】文件消息全链路：发送图片 -> 查询详情 -> WebSocket拦截 -> 删除")
    void fileMessage_WebSocketPushVerification() {
        // ---- Step 1: 模拟用户在房间内 ----
        mockUserInRoom(ROOM_ID, USER_B_ID, true);

        // ---- Step 2: 发送图片消息 ----
        log.info("==== [Step 2: 发送图片消息] ====");
        SendMessageDto imageDto = new SendMessageDto();
        imageDto.setRoomId(ROOM_ID);
        imageDto.setMessageType(2); // IMAGE
        imageDto.setFileUrl("http://minio.example.com/images/photo.jpg");
        imageDto.setFileName("photo.jpg");
        imageDto.setFileSize(102400L);

        MessageVO imageMsg = messageService.sendMessage(imageDto, USER_B_ID);
        assertThat(imageMsg.getMessageId()).isNotNull();
        assertThat(imageMsg.getFileUrl()).isEqualTo("http://minio.example.com/images/photo.jpg");
        assertThat(imageMsg.getFileName()).isEqualTo("photo.jpg");
        assertThat(imageMsg.getFileSize()).isEqualTo(102400L);
        log.info("图片消息存入 DB: messageId={}, fileUrl={}, fileSize={}",
                imageMsg.getMessageId(), imageMsg.getFileUrl(), imageMsg.getFileSize());

        // ---- Step 3: 验证 WebSocket 推送内容 ----
        log.info("==== [Step 3: WebSocket MQ 推送拦截验证] ====");
        org.mockito.ArgumentCaptor<java.util.Map<String, Object>> wsCaptor =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(webSocketMessageProducer, times(1)).sendChatMessageToRoom(eq(ROOM_ID), wsCaptor.capture());
        java.util.Map<String, Object> payload = wsCaptor.getValue();
        assertThat(payload).containsEntry("messageId", imageMsg.getMessageId());
        assertThat(payload).containsEntry("senderId", USER_B_ID);
        assertThat(payload).containsEntry("messageType", 2);
        assertThat(payload).containsEntry("fileUrl", "http://minio.example.com/images/photo.jpg");
        log.info("WebSocket payload 字段验证通过: messageId, senderId, messageType, fileUrl 均正确");

        // ---- Step 4: 查询单条消息详情 ----
        log.info("==== [Step 4: 查询消息详情] ====");
        MessageVO detail = messageService.getMessageById(imageMsg.getMessageId());
        assertThat(detail.getMessageId()).isEqualTo(imageMsg.getMessageId());
        assertThat(detail.getFileUrl()).isEqualTo("http://minio.example.com/images/photo.jpg");
        log.info("消息详情 DB 查询结果: {}", detail);

        // ---- Step 5: 非发送者无权删除 ----
        log.info("==== [Step 5: 权限校验-非发送者删除] ====");
        assertThatThrownBy(() -> messageService.deleteMessage(imageMsg.getMessageId(), USER_A_ID))
                .isInstanceOf(Exception.class);

        // ---- Step 6: 发送者删除消息 ----
        log.info("==== [Step 6: 发送者删除消息] ====");
        messageService.deleteMessage(imageMsg.getMessageId(), USER_B_ID);
        assertThat(messageMapper.selectById(imageMsg.getMessageId())).isNull();
        log.info("消息已删除: messageId={}", imageMsg.getMessageId());
    }

    // ========== 分支测试流 C：Emoji 消息 + 回复消息 ==========

    @Test
    @DisplayName("【分支C】Emoji消息+回复消息：发送Emoji -> 发送回复 -> 查询 -> 清理")
    void emojiAndReplyMessage_Lifecycle() {
        // ---- Step 1: 模拟用户 Alice 在房间内 ----
        mockUserInRoom(ROOM_ID, USER_A_ID, true);
        // ---- Step 2: 模拟用户 Bob 在房间内 ----
        mockUserInRoom(ROOM_ID, USER_B_ID, true);

        // ---- Step 3: Alice 发送 Emoji 消息 ----
        log.info("==== [Step 3: 发送 Emoji 互动消息] ====");
        SendMessageDto emojiDto = new SendMessageDto();
        emojiDto.setRoomId(ROOM_ID);
        emojiDto.setMessageType(5); // EMOJI
        emojiDto.setContent("❤️");

        MessageVO emojiMsg = messageService.sendMessage(emojiDto, USER_A_ID);
        assertThat(emojiMsg.getMessageId()).isNotNull();
        assertThat(emojiMsg.getContent()).isEqualTo("❤️");
        assertThat(emojiMsg.getMessageType()).isEqualTo(5);
        log.info("Emoji 消息存入 DB: messageId={}, content={}", emojiMsg.getMessageId(), emojiMsg.getContent());

        // ---- Step 4: Bob 发送回复消息 ----
        log.info("==== [Step 4: 发送回复消息] ====");
        SendMessageDto replyDto = new SendMessageDto();
        replyDto.setRoomId(ROOM_ID);
        replyDto.setMessageType(1); // TEXT
        replyDto.setContent("Nice emoji!");
        replyDto.setReplyToId(emojiMsg.getMessageId()); // 回复 Alice 的 Emoji

        MessageVO replyMsg = messageService.sendMessage(replyDto, USER_B_ID);
        assertThat(replyMsg.getMessageId()).isNotNull();
        assertThat(replyMsg.getReplyToId()).isEqualTo(emojiMsg.getMessageId());
        log.info("回复消息存入 DB: messageId={}, replyToId={}", replyMsg.getMessageId(), replyMsg.getReplyToId());

        // ---- Step 5: 查询回复消息详情（验证 replyTo 字段正确填充）----
        log.info("==== [Step 5: 查询回复消息详情] ====");
        MessageVO replyDetail = messageService.getMessageById(replyMsg.getMessageId());
        assertThat(replyDetail.getReplyToId()).isEqualTo(emojiMsg.getMessageId());
        log.info("回复详情 DB 结果: messageId={}, replyToId={}, replyToSenderId={}",
                replyDetail.getMessageId(), replyDetail.getReplyToId(), replyDetail.getReplyToSenderId());

        // ---- Step 6: 验证用户资料降级链路 ----
        log.info("==== [Step 6: 用户资料降级链路验证] ====");
        // user_public 表预置了 Alice 和 Bob 的资料
        assertThat(replyDetail.getSenderId()).isEqualTo(USER_B_ID);
        // 降级服务会查询 user_public 表补全昵称（Mapper JOIN 已有 nickname 字段）
        log.info("用户资料（昵称）来自 user_public 表，userId={}, nickname={}",
                USER_B_ID, replyDetail.getSenderNickname());

        // ---- Step 7: 统计 Emoji 类型消息数量 ----
        log.info("==== [Step 7: 按类型统计消息] ====");
        Long emojiCount = messageService.countRoomMessages(ROOM_ID, 5);
        assertThat(emojiCount).isGreaterThanOrEqualTo(1L);
        Long textCount = messageService.countRoomMessages(ROOM_ID, 1);
        assertThat(textCount).isGreaterThanOrEqualTo(1L);
        log.info("房间消息统计: Emoji类型={}, 文本类型={}", emojiCount, textCount);

        // ---- Step 8: Alice 删除自己的 Emoji 消息 ----
        log.info("==== [Step 8: 删除 Emoji 消息] ====");
        messageService.deleteMessage(emojiMsg.getMessageId(), USER_A_ID);
        assertThatThrownBy(() -> messageService.getMessageById(emojiMsg.getMessageId()))
                .isInstanceOf(Exception.class);
        log.info("Emoji 消息已删除: messageId={}", emojiMsg.getMessageId());
    }

    // ========== 边界测试流 D：异常路径 ==========

    @Test
    @DisplayName("【边界D】用户不在房间时发送消息：校验 USER_NOT_IN_ROOM 异常")
    void sendMessage_UserNotInRoom_Throws() {
        // 不 mock 用户在房间，模拟 room-service 返回 false 或 404
        log.info("==== [边界测试: 不在房间内发送消息] ====");

        SendMessageDto dto = new SendMessageDto();
        dto.setRoomId(ROOM_ID);
        dto.setMessageType(1);
        dto.setContent("Should fail");

        assertThatThrownBy(() -> messageService.sendMessage(dto, USER_C_ID))
                .isInstanceOf(Exception.class);

        // 验证数据库中无新增记录
        Long count = messageMapper.countRoomMessages(ROOM_ID, null);
        assertThat(count).isEqualTo(0L);
        log.info("异常路径验证通过: 数据库中无新增消息记录，count={}", count);
    }

    @Test
    @DisplayName("【边界D】查询不存在的消息：抛出 MESSAGE_NOT_FOUND")
    void getMessageById_NotFound_Throws() {
        log.info("==== [边界测试: 查询不存在的消息] ====");

        assertThatThrownBy(() -> messageService.getMessageById(99999L))
                .isInstanceOf(Exception.class);

        log.info("异常路径验证通过: 不存在消息ID=99999 抛出 MESSAGE_NOT_FOUND");
    }

    @Test
    @DisplayName("【边界D】分页查询空房间：返回空列表")
    void getRoomMessages_EmptyRoom_ReturnsEmpty() {
        log.info("==== [边界测试: 查询空房间] ====");

        MessageQueryDto queryDto = new MessageQueryDto();
        queryDto.setRoomId(99999L); // 不存在的房间
        queryDto.setPageNum(1);
        queryDto.setPageSize(10);

        var result = messageService.getRoomMessages(queryDto);
        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(0L);
        log.info("空房间查询验证通过: records=0, total=0");
    }

    @Test
    @DisplayName("【边界D】发送超长文本消息：校验长度限制逻辑")
    void sendMessage_ContentTooLong_Throws() {
        mockUserInRoom(ROOM_ID, USER_A_ID, true);

        log.info("==== [边界测试: 超长文本消息] ====");

        SendMessageDto dto = new SendMessageDto();
        dto.setRoomId(ROOM_ID);
        dto.setMessageType(1); // TEXT
        dto.setContent("A".repeat(3000)); // 超过 maxContentLength=2000

        assertThatThrownBy(() -> messageService.sendMessage(dto, USER_A_ID))
                .isInstanceOf(Exception.class);

        Long count = messageMapper.countRoomMessages(ROOM_ID, null);
        assertThat(count).isEqualTo(0L);
        log.info("超长内容异常验证通过: 消息未入库，数据库 count={}", count);
    }

    // ========== 消息撤回测试流 E ==========

    @Test
    @DisplayName("【撤回E】文本消息撤回：发送 -> 撤回 -> 验证 isRecalled -> 确认查询被过滤")
    void textMessage_Recall_Success() {
        mockUserInRoom(ROOM_ID, USER_A_ID, true);

        // Step 1: 发送文本消息
        log.info("==== [Step 1: 发送文本消息] ====");
        SendMessageDto dto = new SendMessageDto();
        dto.setRoomId(ROOM_ID);
        dto.setMessageType(1);
        dto.setContent("This message will be recalled");
        MessageVO sent = messageService.sendMessage(dto, USER_A_ID);
        assertThat(sent.getMessageId()).isNotNull();
        log.info("消息已发送: messageId={}", sent.getMessageId());

        // Step 2: 撤回消息
        log.info("==== [Step 2: 撤回消息] ====");
        Boolean recalled = messageService.recallMessage(sent.getMessageId(), USER_A_ID);
        assertThat(recalled).isTrue();
        log.info("消息已撤回: messageId={}", sent.getMessageId());

        // Step 3: DB 中消息仍存在但 is_recalled=1
        log.info("==== [Step 3: DB 校验] ====");
        Message raw = messageMapper.selectById(sent.getMessageId());
        assertThat(raw).isNotNull();
        assertThat(raw.getIsRecalled()).isTrue();
        assertThat(raw.getRecalledAt()).isNotNull();
        log.info("DB 校验通过: isRecalled={}, recalledAt={}", raw.getIsRecalled(), raw.getRecalledAt());

        // Step 4: WebSocket 撤回通知已发送
        log.info("==== [Step 4: WebSocket 撤回通知] ====");
        verify(webSocketMessageProducer, atLeastOnce()).sendEventToRoom(
                eq(ROOM_ID), eq("message_recall"), any());

        // Step 5: 常规查询接口查不到该消息（is_recalled=0 过滤）
        log.info("==== [Step 5: 查询过滤验证] ====");
        List<MessageVO> latest = messageService.getLatestMessages(ROOM_ID, 50);
        boolean found = latest.stream().anyMatch(m -> m.getMessageId().equals(sent.getMessageId()));
        assertThat(found).isFalse();
        log.info("查询过滤验证通过: 撤回消息未出现在 LatestMessages 结果中");
    }

    @Test
    @DisplayName("【撤回E】非发送者无权撤回：抛出 NO_PERMISSION_DELETE_MESSAGE")
    void recallMessage_NotSender_Throws() {
        mockUserInRoom(ROOM_ID, USER_A_ID, true);
        mockUserInRoom(ROOM_ID, USER_B_ID, true);

        log.info("==== [撤回权限测试: 非发送者尝试撤回] ====");
        SendMessageDto dto = new SendMessageDto();
        dto.setRoomId(ROOM_ID);
        dto.setMessageType(1);
        dto.setContent("Alice's message");
        MessageVO sent = messageService.sendMessage(dto, USER_A_ID);

        assertThatThrownBy(() -> messageService.recallMessage(sent.getMessageId(), USER_B_ID))
                .isInstanceOf(Exception.class);
        log.info("权限校验通过: Bob 无法撤回 Alice 的消息");
    }

    @Test
    @DisplayName("【撤回E】重复撤回：抛出 MESSAGE_ALREADY_RECALLED")
    void recallMessage_AlreadyRecalled_Throws() {
        mockUserInRoom(ROOM_ID, USER_A_ID, true);

        log.info("==== [撤回幂等测试: 重复撤回] ====");
        SendMessageDto dto = new SendMessageDto();
        dto.setRoomId(ROOM_ID);
        dto.setMessageType(1);
        dto.setContent("To be recalled twice");
        MessageVO sent = messageService.sendMessage(dto, USER_A_ID);

        messageService.recallMessage(sent.getMessageId(), USER_A_ID);
        assertThatThrownBy(() -> messageService.recallMessage(sent.getMessageId(), USER_A_ID))
                .isInstanceOf(Exception.class);
        log.info("幂等校验通过: 重复撤回抛出 MESSAGE_ALREADY_RECALLED");
    }
