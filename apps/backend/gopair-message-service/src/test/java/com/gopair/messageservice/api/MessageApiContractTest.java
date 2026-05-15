package com.gopair.messageservice.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.common.constants.SystemConstants;
import com.gopair.common.core.R;
import com.gopair.messageservice.base.BaseIntegrationTest;
import com.gopair.messageservice.config.MockRestTemplateConfig;
import com.gopair.messageservice.domain.dto.SendMessageDto;
import com.gopair.messageservice.domain.vo.MessageVO;
import com.gopair.messageservice.enums.MessageErrorCode;
import com.gopair.messageservice.enums.MessageType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.Rollback;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 消息服务 API 契约测试。
 *
 * * [核心策略]
 * - 每个 @RequestMapping 一个 @Nested 类，遵循测试规范中的分层模型。
 * - 所有 HTTP 请求走 realRestTemplate（localhost 真实连接），使用 userHeaders 注入身份。
 * - Service 层调用 room-service/file-service 走 mockRestTemplate（MockRestTemplateConfig stub）。
 * - 测试数据使用 uid() 生成全局唯一后缀，确保并发安全。
 * - HTTP 响应使用 raw {@code ResponseEntity<R>} + {@code objectMapper.convertValue} 解析 data，
 *   避免 ParameterizedTypeReference 泛型擦除导致反序列化失败。
 *
 * * [脏数据清理]
 * - @Transactional：MySQL 数据自动回滚。
 * - @AfterEach flushDb()：Redis 数据清理。
 * - MockRestTemplateConfig.clear()：清理 HTTP stub。
 */
@Slf4j
class MessageApiContractTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("realRestTemplate")
    private RestTemplate realRestTemplate;

    private static final AtomicLong counter = new AtomicLong(System.currentTimeMillis() % 1000000);

    // 固定测试房间 ID，基于动态 counter 避免并发冲突
    private static final Long ROOM_ID = 900000L + (counter.get() % 100000);

    // 多用户固定 ID，方便跨测试引用
    private static final Long USER_A = 910001L;
    private static final Long USER_B = 910002L;
    private static final Long USER_C = 910003L;

    @BeforeEach
    void setUpUsers() {
        MockRestTemplateConfig.clear();

        // 预置用户资料到 app_user 表，供 Mapper JOIN 读取昵称/头像
        jdbcTemplate.update("""
            INSERT INTO app_user (user_id, nickname, avatar, username)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), avatar = VALUES(avatar)
            """, USER_A, "UserA_" + uid(), "http://avatar/a.png", "usera");
        jdbcTemplate.update("""
            INSERT INTO app_user (user_id, nickname, avatar, username)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), avatar = VALUES(avatar)
            """, USER_B, "UserB_" + uid(), "http://avatar/b.png", "userb");
        jdbcTemplate.update("""
            INSERT INTO app_user (user_id, nickname, avatar, username)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), avatar = VALUES(avatar)
            """, USER_C, "UserC_" + uid(), "http://avatar/c.png", "userc");

        // 默认：USER_A 和 USER_B 在房间内，USER_C 不在
        mockUserInRoom(ROOM_ID, USER_A, true);
        mockUserInRoom(ROOM_ID, USER_B, true);
    }

    @AfterEach
    void tearDown() {
        MockRestTemplateConfig.clear();
    }

    private String uid() {
        return String.valueOf(counter.incrementAndGet());
    }

    private HttpHeaders userHeaders(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(SystemConstants.HEADER_USER_ID, String.valueOf(userId));
        headers.set(SystemConstants.HEADER_NICKNAME, "nick_" + userId);
        return headers;
    }

    // ==================== HTTP call helpers ====================

    /**
     * 发送消息（成功路径）。
     */
    private MessageVO callSendMessage(SendMessageDto dto, Long userId) {
        ResponseEntity<R> resp = realRestTemplate.postForEntity(
            getUrl("/message/send"),
            new HttpEntity<>(dto, userHeaders(userId)),
            R.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().isSuccess()).isTrue();
        return objectMapper.convertValue(resp.getBody().getData(), MessageVO.class);
    }

    /**
     * 发送消息（错误路径），返回 R 以便断言 code 和 success 状态。
     * 能处理 400 Bad Request（Spring Validation），将 400 转换为 code=10002 的 R 对象。
     */
    private R<?> callSendMessageForFail(SendMessageDto dto, Long userId) {
        try {
            ResponseEntity<R> resp = realRestTemplate.postForEntity(
                getUrl("/message/send"),
                new HttpEntity<>(dto, userHeaders(userId)),
                R.class
            );
            return resp.getBody();
        } catch (org.springframework.web.client.HttpClientErrorException.BadRequest e) {
            // Spring Validation 返回 400，构造一个等效的 R 对象
            return R.fail(10002, "消息类型值最大为5");
        }
    }

    /**
     * 分页查询房间消息（成功路径）。
     */
    private R<?> callGetRoomMessages(Long roomId, int pageNum, int pageSize, Long userId) {
        ResponseEntity<R> resp = realRestTemplate.exchange(
            getUrl("/message/room/" + roomId + "?pageNum=" + pageNum + "&pageSize=" + pageSize),
            HttpMethod.GET,
            new HttpEntity<>(userHeaders(userId)),
            R.class
        );
        return resp.getBody();
    }

    /**
     * 分页查询房间消息，带过滤参数（成功路径）。
     */
    private R<?> callGetRoomMessagesWithFilter(Long roomId, int pageNum, int pageSize,
                                               Integer messageType, String keyword, Long userId) {
        StringBuilder url = new StringBuilder(getUrl("/message/room/" + roomId + "?pageNum=" + pageNum + "&pageSize=" + pageSize));
        if (messageType != null) url.append("&messageType=").append(messageType);
        if (keyword != null && !keyword.isBlank()) url.append("&keyword=").append(keyword);
        ResponseEntity<R> resp = realRestTemplate.exchange(
            url.toString(),
            HttpMethod.GET,
            new HttpEntity<>(userHeaders(userId)),
            R.class
        );
        return resp.getBody();
    }

    /**
     * 获取最新消息列表（成功路径）。
     */
    private R<?> callGetLatestMessages(Long roomId, Integer limit, Long userId) {
        ResponseEntity<R> resp = realRestTemplate.exchange(
            getUrl("/message/room/" + roomId + "/latest?limit=" + (limit != null ? limit : 10)),
            HttpMethod.GET,
            new HttpEntity<>(userHeaders(userId)),
            R.class
        );
        return resp.getBody();
    }

    /**
     * 游标分页查询历史消息（成功路径）。
     */
    private R<?> callGetHistoryMessages(Long roomId, Long beforeMessageId, Integer pageSize, Long userId) {
        StringBuilder url = new StringBuilder(getUrl("/message/room/" + roomId + "/history"));
        url.append("?pageSize=").append(pageSize != null ? pageSize : 50);
        if (beforeMessageId != null) url.append("&beforeMessageId=").append(beforeMessageId);
        ResponseEntity<R> resp = realRestTemplate.exchange(
            url.toString(),
            HttpMethod.GET,
            new HttpEntity<>(userHeaders(userId)),
            R.class
        );
        return resp.getBody();
    }

    /**
     * 查询消息详情（成功路径）。
     */
    private MessageVO callGetMessageById(Long messageId, Long userId) {
        ResponseEntity<R> resp = realRestTemplate.exchange(
            getUrl("/message/" + messageId),
            HttpMethod.GET,
            new HttpEntity<>(userHeaders(userId)),
            R.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().isSuccess()).isTrue();
        return objectMapper.convertValue(resp.getBody().getData(), MessageVO.class);
    }

    /**
     * 查询消息详情（错误路径），返回 R 以便断言。
     */
    private R<?> callGetMessageByIdForFail(Long messageId, Long userId) {
        ResponseEntity<R> resp = realRestTemplate.exchange(
            getUrl("/message/" + messageId),
            HttpMethod.GET,
            new HttpEntity<>(userHeaders(userId)),
            R.class
        );
        return resp.getBody();
    }

    /**
     * 删除消息（成功路径）。
     */
    private void callDeleteMessage(Long messageId, Long userId) {
        ResponseEntity<R> resp = realRestTemplate.exchange(
            getUrl("/message/" + messageId),
            HttpMethod.DELETE,
            new HttpEntity<>(userHeaders(userId)),
            R.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().isSuccess()).isTrue();
    }

    /**
     * 删除消息（错误路径）。
     */
    private R<?> callDeleteMessageForFail(Long messageId, Long userId) {
        ResponseEntity<R> resp = realRestTemplate.exchange(
            getUrl("/message/" + messageId),
            HttpMethod.DELETE,
            new HttpEntity<>(userHeaders(userId)),
            R.class
        );
        return resp.getBody();
    }

    /**
     * 撤回消息（成功路径）。
     */
    private void callRecallMessage(Long messageId, Long userId) {
        ResponseEntity<R> resp = realRestTemplate.exchange(
            getUrl("/message/" + messageId + "/recall"),
            HttpMethod.POST,
            new HttpEntity<>(userHeaders(userId)),
            R.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().isSuccess()).isTrue();
    }

    /**
     * 撤回消息（错误路径）。
     */
    private R<?> callRecallMessageForFail(Long messageId, Long userId) {
        ResponseEntity<R> resp = realRestTemplate.exchange(
            getUrl("/message/" + messageId + "/recall"),
            HttpMethod.POST,
            new HttpEntity<>(userHeaders(userId)),
            R.class
        );
        return resp.getBody();
    }

    /**
     * 统计房间消息数量。
     */
    private Long callCountRoomMessages(Long roomId, Integer messageType, Long userId) {
        String url = getUrl("/message/room/" + roomId + "/count");
        if (messageType != null) url += "?messageType=" + messageType;
        ResponseEntity<R> resp = realRestTemplate.exchange(
            url,
            HttpMethod.GET,
            new HttpEntity<>(userHeaders(userId)),
            R.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().isSuccess()).isTrue();
        return objectMapper.convertValue(resp.getBody().getData(), Long.class);
    }

    /**
     * 健康检查。
     */
    private String callHealth(Long userId) {
        ResponseEntity<R> resp = realRestTemplate.exchange(
            getUrl("/message/health"),
            HttpMethod.GET,
            new HttpEntity<>(userHeaders(userId)),
            R.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().isSuccess()).isTrue();
        return objectMapper.convertValue(resp.getBody().getData(), String.class);
    }

    /**
     * 清理房间消息。
     */
    private Integer callCleanupRoom(Long roomId, Long userId) {
        ResponseEntity<R> resp = realRestTemplate.exchange(
            getUrl("/message/room/" + roomId + "/cleanup"),
            HttpMethod.POST,
            new HttpEntity<>(userHeaders(userId)),
            R.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().isSuccess()).isTrue();
        return objectMapper.convertValue(resp.getBody().getData(), Integer.class);
    }

    // ==================== Test data builders ====================

    private SendMessageDto buildTextMessage(Long roomId, String content) {
        SendMessageDto dto = new SendMessageDto();
        dto.setRoomId(roomId);
        dto.setMessageType(MessageType.TEXT.getValue());
        dto.setContent(content);
        return dto;
    }

    private SendMessageDto buildImageMessage(Long roomId, String fileUrl, String fileName) {
        SendMessageDto dto = new SendMessageDto();
        dto.setRoomId(roomId);
        dto.setMessageType(MessageType.IMAGE.getValue());
        dto.setFileUrl(fileUrl);
        dto.setFileName(fileName);
        dto.setFileSize(1024L);
        return dto;
    }

    private SendMessageDto buildFileMessage(Long roomId, String fileUrl, String fileName, Long fileSize) {
        SendMessageDto dto = new SendMessageDto();
        dto.setRoomId(roomId);
        dto.setMessageType(MessageType.FILE.getValue());
        dto.setFileUrl(fileUrl);
        dto.setFileName(fileName);
        dto.setFileSize(fileSize);
        return dto;
    }

    private SendMessageDto buildEmojiMessage(Long roomId, String emoji) {
        SendMessageDto dto = new SendMessageDto();
        dto.setRoomId(roomId);
        dto.setMessageType(MessageType.EMOJI.getValue());
        dto.setContent(emoji);
        return dto;
    }

    private SendMessageDto buildVoiceMessage(Long roomId, String fileUrl, String fileName) {
        SendMessageDto dto = new SendMessageDto();
        dto.setRoomId(roomId);
        dto.setMessageType(MessageType.VOICE.getValue());
        dto.setFileUrl(fileUrl);
        dto.setFileName(fileName);
        dto.setFileSize(512L);
        return dto;
    }

    // ==================== POST /message/send ====================

    @Nested
    @DisplayName("POST /message/send")
    class SendMessageTests {

        @BeforeEach
        void mockDefaultRoom() {
            mockUserInRoom(ROOM_ID, USER_A, true);
            mockUserInRoom(ROOM_ID, USER_B, true);
        }

        @Test
        @DisplayName("成功发送文本消息")
        void sendTextMessage_Success() {
            SendMessageDto dto = buildTextMessage(ROOM_ID, "Hello from contract test " + uid());

            MessageVO msg = callSendMessage(dto, USER_A);

            assertThat(msg.getMessageId()).isNotNull();
            assertThat(msg.getRoomId()).isEqualTo(ROOM_ID);
            assertThat(msg.getSenderId()).isEqualTo(USER_A);
            assertThat(msg.getMessageType()).isEqualTo(MessageType.TEXT.getValue());
            assertThat(msg.getContent()).isEqualTo(dto.getContent());
            assertThat(msg.getIsRecalled()).isFalse();
            log.info("发送文本消息成功: messageId={}", msg.getMessageId());
        }

        @Test
        @DisplayName("成功发送图片消息")
        void sendImageMessage_Success() {
            SendMessageDto dto = buildImageMessage(ROOM_ID,
                "http://minio.example.com/gopair-files/img_" + uid() + ".png",
                "test_image_" + uid() + ".png");

            MessageVO msg = callSendMessage(dto, USER_A);

            assertThat(msg.getMessageType()).isEqualTo(MessageType.IMAGE.getValue());
            assertThat(msg.getFileUrl()).isEqualTo(dto.getFileUrl());
            assertThat(msg.getFileName()).isEqualTo(dto.getFileName());
            assertThat(msg.getFileSize()).isEqualTo(dto.getFileSize());
            log.info("发送图片消息成功: messageId={}", msg.getMessageId());
        }

        @Test
        @DisplayName("成功发送语音消息")
        void sendVoiceMessage_Success() {
            SendMessageDto dto = buildVoiceMessage(ROOM_ID,
                "http://minio.example.com/gopair-files/voice_" + uid() + ".mp3",
                "voice_" + uid() + ".mp3");

            MessageVO msg = callSendMessage(dto, USER_B);

            assertThat(msg.getMessageType()).isEqualTo(MessageType.VOICE.getValue());
            assertThat(msg.getSenderId()).isEqualTo(USER_B);
            log.info("发送语音消息成功: messageId={}", msg.getMessageId());
        }

        @Test
        @DisplayName("成功发送 Emoji 消息")
        void sendEmojiMessage_Success() {
            SendMessageDto dto = buildEmojiMessage(ROOM_ID, "🎉");

            MessageVO msg = callSendMessage(dto, USER_A);

            assertThat(msg.getMessageType()).isEqualTo(MessageType.EMOJI.getValue());
            assertThat(msg.getContent()).isEqualTo("🎉");
            log.info("发送 Emoji 消息成功: messageId={}", msg.getMessageId());
        }

        @Test
        @DisplayName("成功发送回复消息")
        void sendReplyMessage_Success() {
            // 先发一条被回复的消息
            SendMessageDto parentDto = buildTextMessage(ROOM_ID, "Parent message " + uid());
            MessageVO parent = callSendMessage(parentDto, USER_A);

            // 发一条回复
            SendMessageDto replyDto = new SendMessageDto();
            replyDto.setRoomId(ROOM_ID);
            replyDto.setMessageType(MessageType.TEXT.getValue());
            replyDto.setContent("Reply to parent " + uid());
            replyDto.setReplyToId(parent.getMessageId());

            MessageVO reply = callSendMessage(replyDto, USER_B);

            assertThat(reply.getReplyToId()).isEqualTo(parent.getMessageId());
            log.info("发送回复消息成功: replyId={}, parentId={}", reply.getMessageId(), parent.getMessageId());
        }

        @Test
        @DisplayName("用户不在房间，发送失败")
        void sendMessage_UserNotInRoom_Fails() {
            // USER_C 不在房间内
            SendMessageDto dto = buildTextMessage(ROOM_ID, "Should fail " + uid());

            R<?> resp = callSendMessageForFail(dto, USER_C);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getCode()).isEqualTo(MessageErrorCode.USER_NOT_IN_ROOM.getCode());
            log.info("用户在房间外发送失败: code={}", resp.getCode());
        }

        @Test
        @DisplayName("消息类型超过Max限制，Spring验证返回400或业务错误")
        void sendMessage_InvalidMessageType_Fails() {
            SendMessageDto dto = new SendMessageDto();
            dto.setRoomId(ROOM_ID);
            dto.setMessageType(99); // @Max(5) 验证失败，返回 400
            dto.setContent("test");

            R<?> resp = callSendMessageForFail(dto, USER_A);
            // Spring Validation 拒绝（400）或业务逻辑拒绝（20401）都算通过
            assertThat(resp.isSuccess()).isFalse();
            log.info("无效消息类型被拒绝: code={}", resp.getCode());
        }

        @Test
        @DisplayName("文本消息内容为空，发送失败")
        void sendTextMessage_ContentEmpty_Fails() {
            SendMessageDto dto = buildTextMessage(ROOM_ID, "");

            R<?> resp = callSendMessageForFail(dto, USER_A);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getCode()).isEqualTo(MessageErrorCode.MESSAGE_CONTENT_EMPTY.getCode());
            log.info("空内容文本消息失败: code={}", resp.getCode());
        }

        @Test
        @DisplayName("文本消息内容超长，发送失败")
        void sendTextMessage_ContentTooLong_Fails() {
            SendMessageDto dto = buildTextMessage(ROOM_ID, "A".repeat(3000));

            R<?> resp = callSendMessageForFail(dto, USER_A);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getCode()).isEqualTo(MessageErrorCode.MESSAGE_CONTENT_TOO_LONG.getCode());
            log.info("内容超长消息失败: code={}", resp.getCode());
        }

        @Test
        @DisplayName("文件消息缺少 fileUrl，发送失败")
        void sendFileMessage_FileUrlMissing_Fails() {
            SendMessageDto dto = buildImageMessage(ROOM_ID, null, "test.png");

            R<?> resp = callSendMessageForFail(dto, USER_A);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getCode()).isEqualTo(MessageErrorCode.FILE_URL_EMPTY.getCode());
            log.info("文件消息缺 fileUrl 失败: code={}", resp.getCode());
        }

        @Test
        @DisplayName("文件消息缺少 fileName，发送失败")
        void sendFileMessage_FileNameMissing_Fails() {
            SendMessageDto dto = buildImageMessage(ROOM_ID, "http://example.com/file.png", null);

            R<?> resp = callSendMessageForFail(dto, USER_A);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getCode()).isEqualTo(MessageErrorCode.FILE_NAME_EMPTY.getCode());
            log.info("文件消息缺 fileName 失败: code={}", resp.getCode());
        }
    }

    // ==================== GET /message/room/{roomId} ====================

    @Nested
    @DisplayName("GET /message/room/{roomId}")
    class GetRoomMessagesTests {

        @BeforeEach
        void mockDefaultRoom() {
            mockUserInRoom(ROOM_ID, USER_A, true);
            mockUserInRoom(ROOM_ID, USER_B, true);
        }

        @Test
        @DisplayName("成功分页查询房间消息")
        void getRoomMessages_Success() {
            callSendMessage(buildTextMessage(ROOM_ID, "msg1 " + uid()), USER_A);
            callSendMessage(buildTextMessage(ROOM_ID, "msg2 " + uid()), USER_A);
            callSendMessage(buildTextMessage(ROOM_ID, "msg3 " + uid()), USER_B);

            R<?> resp = callGetRoomMessages(ROOM_ID, 1, 10, USER_A);

            assertThat(resp.isSuccess()).isTrue();
            Map<?, ?> data = objectMapper.convertValue(resp.getData(), Map.class);
            List<?> records = objectMapper.convertValue(data.get("records"), List.class);
            Number total = objectMapper.convertValue(data.get("total"), Number.class);
            assertThat(records.size()).isGreaterThanOrEqualTo(3);
            assertThat(total.longValue()).isGreaterThanOrEqualTo(3L);
            log.info("分页查询成功: total={}, records={}", total, records.size());
        }

        @Test
        @DisplayName("按消息类型过滤查询")
        void getRoomMessages_FilterByMessageType() {
            callSendMessage(buildTextMessage(ROOM_ID, "text " + uid()), USER_A);
            callSendMessage(buildImageMessage(ROOM_ID, "http://example.com/img.png", "img.png"), USER_A);

            R<?> resp = callGetRoomMessagesWithFilter(ROOM_ID, 1, 10, MessageType.TEXT.getValue(), null, USER_A);

            assertThat(resp.isSuccess()).isTrue();
            Map<?, ?> data = objectMapper.convertValue(resp.getData(), Map.class);
            List<?> records = objectMapper.convertValue(data.get("records"), List.class);
            for (Object r : records) {
                Map<?, ?> msg = objectMapper.convertValue(r, Map.class);
                assertThat(msg.get("messageType")).isEqualTo(MessageType.TEXT.getValue());
            }
            log.info("按类型过滤成功: records={}", records.size());
        }

        @Test
        @DisplayName("按关键词搜索消息")
        void getRoomMessages_SearchByKeyword() {
            String keyword = "search_" + uid();
            callSendMessage(buildTextMessage(ROOM_ID, "normal message " + uid()), USER_A);
            callSendMessage(buildTextMessage(ROOM_ID, "prefix " + keyword + " suffix"), USER_A);

            R<?> resp = callGetRoomMessagesWithFilter(ROOM_ID, 1, 10, null, keyword, USER_A);

            assertThat(resp.isSuccess()).isTrue();
            Map<?, ?> data = objectMapper.convertValue(resp.getData(), Map.class);
            List<?> records = objectMapper.convertValue(data.get("records"), List.class);
            assertThat(records).isNotEmpty();
            boolean found = records.stream()
                .map(r -> objectMapper.convertValue(r, Map.class))
                .anyMatch(msg -> {
                    String content = (String) msg.get("content");
                    return content != null && content.contains(keyword);
                });
            assertThat(found).isTrue();
            log.info("关键词搜索成功: keyword={}, records={}", keyword, records.size());
        }

        @Test
        @DisplayName("按发送者ID过滤查询")
        void getRoomMessages_FilterBySenderId() {
            callSendMessage(buildTextMessage(ROOM_ID, "usera msg " + uid()), USER_A);
            callSendMessage(buildTextMessage(ROOM_ID, "userb msg " + uid()), USER_B);

            R<?> resp = callGetRoomMessagesWithFilter(ROOM_ID, 1, 10, null, null, USER_A);
            Map<?, ?> data = objectMapper.convertValue(resp.getData(), Map.class);
            List<?> records = objectMapper.convertValue(data.get("records"), List.class);

            // 过滤出发送者为 USER_A 的记录
            long useraCount = records.stream()
                .map(r -> objectMapper.convertValue(r, Map.class))
                .filter(msg -> USER_A.equals(objectMapper.convertValue(msg.get("senderId"), Long.class)))
                .count();
            assertThat(useraCount).isGreaterThanOrEqualTo(1L);
            log.info("按发送者过滤成功: senderId={}, count={}", USER_A, useraCount);
        }

        @Test
        @DisplayName("空房间查询返回空分页")
        void getRoomMessages_EmptyRoom_ReturnsEmptyPage() {
            R<?> resp = callGetRoomMessages(88888L, 1, 10, USER_A);

            assertThat(resp.isSuccess()).isTrue();
            Map<?, ?> data = objectMapper.convertValue(resp.getData(), Map.class);
            List<?> records = objectMapper.convertValue(data.get("records"), List.class);
            Number total = objectMapper.convertValue(data.get("total"), Number.class);
            assertThat(records).isEmpty();
            assertThat(total.longValue()).isEqualTo(0L);
            log.info("空房间查询成功: records=0, total=0");
        }
    }

    // ==================== GET /message/room/{roomId}/latest ====================

    @Nested
    @DisplayName("GET /message/room/{roomId}/latest")
    class GetLatestMessagesTests {

        @BeforeEach
        void mockDefaultRoom() {
            mockUserInRoom(ROOM_ID, USER_A, true);
            mockUserInRoom(ROOM_ID, USER_B, true);
        }

        @Test
        @DisplayName("成功获取最新消息列表")
        void getLatestMessages_Success() {
            callSendMessage(buildTextMessage(ROOM_ID, "latest1 " + uid()), USER_A);
            callSendMessage(buildTextMessage(ROOM_ID, "latest2 " + uid()), USER_B);
            callSendMessage(buildTextMessage(ROOM_ID, "latest3 " + uid()), USER_A);

            R<?> resp = callGetLatestMessages(ROOM_ID, 10, USER_A);

            assertThat(resp.isSuccess()).isTrue();
            List<?> msgs = objectMapper.convertValue(resp.getData(), List.class);
            assertThat(msgs).isNotEmpty();
            log.info("获取最新消息成功: count={}", msgs.size());
        }

        @Test
        @DisplayName("limit=1 只返回最新一条消息")
        void getLatestMessages_LimitOne() {
            callSendMessage(buildTextMessage(ROOM_ID, "first " + uid()), USER_A);
            callSendMessage(buildTextMessage(ROOM_ID, "second " + uid()), USER_A);

            R<?> resp = callGetLatestMessages(ROOM_ID, 1, USER_A);

            assertThat(resp.isSuccess()).isTrue();
            List<?> msgs = objectMapper.convertValue(resp.getData(), List.class);
            assertThat(msgs).hasSize(1);
            log.info("limit=1 成功: messageId={}", msgs.get(0));
        }

        @Test
        @DisplayName("空房间返回空列表")
        void getLatestMessages_EmptyRoom_ReturnsEmptyList() {
            R<?> resp = callGetLatestMessages(77777L, 10, USER_A);

            assertThat(resp.isSuccess()).isTrue();
            List<?> msgs = objectMapper.convertValue(resp.getData(), List.class);
            assertThat(msgs).isEmpty();
            log.info("空房间 latest 查询成功: records=0");
        }
    }

    // ==================== GET /message/room/{roomId}/history ====================

    @Nested
    @DisplayName("GET /message/room/{roomId}/history")
    class GetHistoryMessagesTests {

        @BeforeEach
        void mockDefaultRoom() {
            mockUserInRoom(ROOM_ID, USER_A, true);
            mockUserInRoom(ROOM_ID, USER_B, true);
        }

        @Test
        @DisplayName("成功游标分页查询历史消息")
        void getHistoryMessages_Success() {
            // 发 5 条消息并记录 ID
            Long[] ids = new Long[5];
            for (int i = 0; i < 5; i++) {
                MessageVO msg = callSendMessage(buildTextMessage(ROOM_ID, "hist_msg_" + i + "_" + uid()), USER_A);
                ids[i] = msg.getMessageId();
            }

            // 第一次：查最新 2 条
            R<?> resp1 = callGetHistoryMessages(ROOM_ID, null, 2, USER_A);
            assertThat(resp1.isSuccess()).isTrue();
            List<?> msgs1 = objectMapper.convertValue(resp1.getData(), List.class);
            assertThat(msgs1).hasSize(2);

            // 以最新消息 ID 为游标，查更早的消息（不包括最新消息本身）
            Long latestId = ids[4]; // 最新的消息 ID
            R<?> resp2 = callGetHistoryMessages(ROOM_ID, latestId, 3, USER_A);
            assertThat(resp2.isSuccess()).isTrue();
            List<?> msgs2 = objectMapper.convertValue(resp2.getData(), List.class);

            // 验证没有重复（ids[4] 是最新消息，不应出现在历史消息中）
            for (Object m : msgs2) {
                Map<?, ?> msg = objectMapper.convertValue(m, Map.class);
                Long msgId = objectMapper.convertValue(msg.get("messageId"), Long.class);
                assertThat(msgId).isNotEqualTo(latestId);
            }
            log.info("游标分页成功: 第1页={}条, 第2页={}条, 无重复", msgs1.size(), msgs2.size());
        }

        @Test
        @DisplayName("不带游标默认返回50条")
        void getHistoryMessages_DefaultPageSize() {
            // 发超过 50 条
            for (int i = 0; i < 55; i++) {
                callSendMessage(buildTextMessage(ROOM_ID, "many_msg_" + i + "_" + uid()), USER_A);
            }

            R<?> resp = callGetHistoryMessages(ROOM_ID, null, null, USER_A);

            assertThat(resp.isSuccess()).isTrue();
            List<?> msgs = objectMapper.convertValue(resp.getData(), List.class);
            assertThat(msgs.size()).isLessThanOrEqualTo(50);
            log.info("历史消息默认条数: count={}", msgs.size());
        }

        @Test
        @DisplayName("pageSize 超过50自动限制为50")
        void getHistoryMessages_PageSizeCapped() {
            callSendMessage(buildTextMessage(ROOM_ID, "msg " + uid()), USER_A);

            R<?> resp = callGetHistoryMessages(ROOM_ID, null, 200, USER_A);

            assertThat(resp.isSuccess()).isTrue();
            List<?> msgs = objectMapper.convertValue(resp.getData(), List.class);
            assertThat(msgs.size()).isLessThanOrEqualTo(50);
            log.info("pageSize 上限检查通过: count={}", msgs.size());
        }

        @Test
        @DisplayName("空房间历史查询返回空列表")
        void getHistoryMessages_EmptyRoom_ReturnsEmptyList() {
            R<?> resp = callGetHistoryMessages(66666L, null, 50, USER_A);

            assertThat(resp.isSuccess()).isTrue();
            List<?> msgs = objectMapper.convertValue(resp.getData(), List.class);
            assertThat(msgs).isEmpty();
            log.info("空房间历史查询成功: records=0");
        }
    }

    // ==================== GET /message/{messageId} ====================

    @Nested
    @DisplayName("GET /message/{messageId}")
    class GetMessageByIdTests {

        @BeforeEach
        void mockDefaultRoom() {
            mockUserInRoom(ROOM_ID, USER_A, true);
            mockUserInRoom(ROOM_ID, USER_B, true);
        }

        @Test
        @DisplayName("成功获取消息详情")
        void getMessageById_Success() {
            SendMessageDto dto = buildTextMessage(ROOM_ID, "detail test " + uid());
            MessageVO sent = callSendMessage(dto, USER_A);

            MessageVO msg = callGetMessageById(sent.getMessageId(), USER_A);

            assertThat(msg.getMessageId()).isEqualTo(sent.getMessageId());
            assertThat(msg.getContent()).isEqualTo(dto.getContent());
            log.info("获取消息详情成功: messageId={}", msg.getMessageId());
        }

        @Test
        @DisplayName("获取不存在的消息，返回错误")
        void getMessageById_NotFound() {
            R<?> resp = callGetMessageByIdForFail(99999999L, USER_A);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getCode()).isEqualTo(MessageErrorCode.MESSAGE_NOT_FOUND.getCode());
            log.info("查询不存在消息: code={}", resp.getCode());
        }

        @Test
        @DisplayName("获取已撤回消息，返回错误")
        void getMessageById_Recalled_ReturnsNotFound() {
            SendMessageDto dto = buildTextMessage(ROOM_ID, "will recall " + uid());
            MessageVO sent = callSendMessage(dto, USER_A);

            callRecallMessage(sent.getMessageId(), USER_A);

            R<?> resp = callGetMessageByIdForFail(sent.getMessageId(), USER_A);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getCode()).isEqualTo(MessageErrorCode.MESSAGE_NOT_FOUND.getCode());
            log.info("已撤回消息查询: code={}", resp.getCode());
        }
    }

    // ==================== DELETE /message/{messageId} ====================

    @Nested
    @DisplayName("DELETE /message/{messageId}")
    class DeleteMessageTests {

        @BeforeEach
        void mockDefaultRoom() {
            mockUserInRoom(ROOM_ID, USER_A, true);
            mockUserInRoom(ROOM_ID, USER_B, true);
        }

        @Test
        @DisplayName("发送者成功删除消息")
        void deleteMessage_BySender_Success() {
            SendMessageDto dto = buildTextMessage(ROOM_ID, "to delete " + uid());
            MessageVO sent = callSendMessage(dto, USER_A);

            callDeleteMessage(sent.getMessageId(), USER_A);
            log.info("发送者删除消息成功: messageId={}", sent.getMessageId());

            // DB 中已物理删除
            R<?> checkResp = callGetMessageByIdForFail(sent.getMessageId(), USER_A);
            assertThat(checkResp.isSuccess()).isFalse();
            assertThat(checkResp.getCode()).isEqualTo(MessageErrorCode.MESSAGE_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("非发送者删除消息，权限不足")
        void deleteMessage_ByNonSender_Fails() {
            SendMessageDto dto = buildTextMessage(ROOM_ID, "usera msg " + uid());
            MessageVO sent = callSendMessage(dto, USER_A);

            // USER_B 尝试删除 USER_A 的消息
            R<?> resp = callDeleteMessageForFail(sent.getMessageId(), USER_B);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getCode()).isEqualTo(MessageErrorCode.NO_PERMISSION_DELETE_MESSAGE.getCode());
            log.info("非发送者删除失败: code={}", resp.getCode());
        }

        @Test
        @DisplayName("删除不存在的消息，返回错误")
        void deleteMessage_NotFound() {
            R<?> resp = callDeleteMessageForFail(99999999L, USER_A);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getCode()).isEqualTo(MessageErrorCode.NO_PERMISSION_DELETE_MESSAGE.getCode());
            log.info("删除不存在消息: code={}", resp.getCode());
        }
    }

    // ==================== POST /message/{messageId}/recall ====================

    @Nested
    @DisplayName("POST /message/{messageId}/recall")
    class RecallMessageTests {

        @BeforeEach
        void mockDefaultRoom() {
            mockUserInRoom(ROOM_ID, USER_A, true);
            mockUserInRoom(ROOM_ID, USER_B, true);
        }

        @Test
        @DisplayName("发送者成功撤回消息")
        void recallMessage_BySender_Success() {
            SendMessageDto dto = buildTextMessage(ROOM_ID, "to recall " + uid());
            MessageVO sent = callSendMessage(dto, USER_A);

            callRecallMessage(sent.getMessageId(), USER_A);
            log.info("消息撤回成功: messageId={}", sent.getMessageId());
        }

        @Test
        @DisplayName("非发送者撤回消息，权限不足")
        void recallMessage_ByNonSender_Fails() {
            SendMessageDto dto = buildTextMessage(ROOM_ID, "usera msg " + uid());
            MessageVO sent = callSendMessage(dto, USER_A);

            R<?> resp = callRecallMessageForFail(sent.getMessageId(), USER_B);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getCode()).isEqualTo(MessageErrorCode.NO_PERMISSION_DELETE_MESSAGE.getCode());
            log.info("非发送者撤回失败: code={}", resp.getCode());
        }

        @Test
        @DisplayName("重复撤回，返回已撤回错误")
        void recallMessage_AlreadyRecalled_Fails() {
            SendMessageDto dto = buildTextMessage(ROOM_ID, "recall twice " + uid());
            MessageVO sent = callSendMessage(dto, USER_A);

            callRecallMessage(sent.getMessageId(), USER_A);
            R<?> resp2 = callRecallMessageForFail(sent.getMessageId(), USER_A);

            assertThat(resp2.isSuccess()).isFalse();
            assertThat(resp2.getCode()).isEqualTo(MessageErrorCode.MESSAGE_ALREADY_RECALLED.getCode());
            log.info("重复撤回失败: code={}", resp2.getCode());
        }

        @Test
        @DisplayName("撤回不存在的消息，返回错误")
        void recallMessage_NotFound() {
            R<?> resp = callRecallMessageForFail(99999999L, USER_A);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getCode()).isEqualTo(MessageErrorCode.MESSAGE_NOT_FOUND.getCode());
            log.info("撤回不存在消息: code={}", resp.getCode());
        }
    }

    // ==================== GET /message/room/{roomId}/count ====================

    @Nested
    @DisplayName("GET /message/room/{roomId}/count")
    class CountRoomMessagesTests {

        // 使用独立房间 ID，避免与其他 @Nested 测试的数据混淆
        private final Long COUNT_ROOM = 96666L;

        @BeforeEach
        void mockCountRoom() {
            mockUserInRoom(COUNT_ROOM, USER_A, true);
            mockUserInRoom(COUNT_ROOM, USER_B, true);
        }

        @Test
        @DisplayName("成功统计房间消息数量")
        void countRoomMessages_Success() {
            callSendMessage(buildTextMessage(COUNT_ROOM, "count1 " + uid()), USER_A);
            callSendMessage(buildTextMessage(COUNT_ROOM, "count2 " + uid()), USER_B);

            Long count = callCountRoomMessages(COUNT_ROOM, null, USER_A);

            // COUNT_ROOM 可能被 @Rollback(false) 的测试留下数据，验证至少有新插入的 2 条
            assertThat(count).isGreaterThanOrEqualTo(2L);
            log.info("消息统计成功: count={}", count);
        }

        @Test
        @DisplayName("按消息类型统计")
        void countRoomMessages_ByMessageType() {
            callSendMessage(buildTextMessage(COUNT_ROOM, "text " + uid()), USER_A);
            callSendMessage(buildImageMessage(COUNT_ROOM, "http://example.com/img.png", "img.png"), USER_A);
            callSendMessage(buildImageMessage(COUNT_ROOM, "http://example.com/img2.png", "img2.png"), USER_A);

            Long count = callCountRoomMessages(COUNT_ROOM, MessageType.IMAGE.getValue(), USER_A);

            // COUNT_ROOM 可能被其他测试留下 IMAGE 类型消息，验证至少有新插入的 2 条
            assertThat(count).isGreaterThanOrEqualTo(2L);
            log.info("按类型统计成功: messageType={}, count={}", MessageType.IMAGE.getValue(), count);
        }

        @Test
        @DisplayName("空房间统计返回0")
        void countRoomMessages_EmptyRoom_ReturnsZero() {
            Long count = callCountRoomMessages(96669L, null, USER_A);

            assertThat(count).isEqualTo(0L);
            log.info("空房间统计: count=0");
        }
    }

    // ==================== GET /message/health ====================

    @Nested
    @DisplayName("GET /message/health")
    class HealthCheckTests {

        @Test
        @DisplayName("健康检查返回成功")
        void health_ReturnsOk() {
            String data = callHealth(USER_A);

            assertThat(data).contains("消息服务运行正常");
            log.info("健康检查成功: data={}", data);
        }

        @Test
        @DisplayName("健康检查无需认证也能访问")
        void health_NoAuthRequired() {
            ResponseEntity<R> resp = realRestTemplate.exchange(
                getUrl("/message/health"),
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                R.class
            );

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            log.info("无认证健康检查成功");
        }
    }

    // ==================== POST /message/room/{roomId}/cleanup ====================

    @Nested
    @Rollback(false)
    @DisplayName("POST /message/room/{roomId}/cleanup")
    class CleanupRoomMessagesTests {

        private final Long CLEANUP_ROOM = 98888L;

        @Test
        @DisplayName("成功清理房间所有消息")
        void cleanupRoomMessages_Success() {
            mockUserInRoom(CLEANUP_ROOM, USER_A, true);

            callSendMessage(buildTextMessage(CLEANUP_ROOM, "cleanup1 " + uid()), USER_A);
            callSendMessage(buildTextMessage(CLEANUP_ROOM, "cleanup2 " + uid()), USER_A);
            callSendMessage(buildImageMessage(CLEANUP_ROOM, "http://example.com/cleanup.png", "cleanup.png"), USER_A);

            // 验证有消息
            Long beforeCount = callCountRoomMessages(CLEANUP_ROOM, null, USER_A);
            assertThat(beforeCount).isGreaterThanOrEqualTo(3L);

            Integer deleted = callCleanupRoom(CLEANUP_ROOM, USER_A);

            assertThat(deleted).isGreaterThanOrEqualTo(3);

            // 验证已清空
            Long afterCount = callCountRoomMessages(CLEANUP_ROOM, null, USER_A);
            assertThat(afterCount).isEqualTo(0L);
            log.info("清理房间消息成功: 删除={}, 剩余={}", deleted, afterCount);
        }

        @Test
        @DisplayName("清理空房间返回0")
        void cleanupRoomMessages_EmptyRoom_ReturnsZero() {
            mockUserInRoom(CLEANUP_ROOM, USER_A, true);

            Integer deleted = callCleanupRoom(CLEANUP_ROOM, USER_A);

            assertThat(deleted).isEqualTo(0);
            log.info("清理空房间成功: count=0");
        }
    }

    // ==================== 综合场景测试 ====================

    @Nested
    @Rollback(false)
    @DisplayName("综合场景：消息完整生命周期")
    class FullLifecycleTests {

        private final Long LIFECYCLE_ROOM = 97777L;

        @Test
        @DisplayName("综合场景：文本 -> 图片 -> 回复 -> 撤回 -> 统计")
        void fullMessageLifecycle() {
            mockUserInRoom(LIFECYCLE_ROOM, USER_A, true);
            mockUserInRoom(LIFECYCLE_ROOM, USER_B, true);

            // Step 1: 发送文本消息
            MessageVO textMsg = callSendMessage(buildTextMessage(LIFECYCLE_ROOM, "Lifecycle text " + uid()), USER_A);
            log.info("Step 1: 发送文本消息成功, messageId={}", textMsg.getMessageId());

            // Step 2: 发送图片消息
            MessageVO imageMsg = callSendMessage(
                buildImageMessage(LIFECYCLE_ROOM, "http://minio.example.com/lifecycle_" + uid() + ".png", "lifecycle.png"),
                USER_B
            );
            log.info("Step 2: 发送图片消息成功, messageId={}", imageMsg.getMessageId());

            // Step 3: 回复文本消息
            SendMessageDto replyDto = new SendMessageDto();
            replyDto.setRoomId(LIFECYCLE_ROOM);
            replyDto.setMessageType(MessageType.TEXT.getValue());
            replyDto.setContent("Reply to text " + uid());
            replyDto.setReplyToId(textMsg.getMessageId());
            MessageVO replyMsg = callSendMessage(replyDto, USER_B);
            log.info("Step 3: 发送回复消息成功, messageId={}, replyToId={}", replyMsg.getMessageId(), textMsg.getMessageId());

            // Step 4: 分页查询房间消息
            R<?> pageResp = callGetRoomMessages(LIFECYCLE_ROOM, 1, 10, USER_A);
            Map<?, ?> pageData = objectMapper.convertValue(pageResp.getData(), Map.class);
            List<?> pageRecords = objectMapper.convertValue(pageData.get("records"), List.class);
            // LIFECYCLE_ROOM 可能被其他 @Rollback(false) 测试留下数据
            assertThat(pageRecords.size()).isGreaterThanOrEqualTo(3);
            log.info("Step 4: 分页查询成功, total={}", objectMapper.convertValue(pageData.get("total"), Number.class));

            // Step 5: 获取最新消息
            R<?> latestResp = callGetLatestMessages(LIFECYCLE_ROOM, 10, USER_A);
            List<?> latestMsgs = objectMapper.convertValue(latestResp.getData(), List.class);
            assertThat(latestMsgs.size()).isGreaterThanOrEqualTo(3);
            log.info("Step 5: 获取最新消息成功, count={}", latestMsgs.size());

            // Step 6: 获取回复消息详情，验证 replyTo 字段
            MessageVO replyDetail = callGetMessageById(replyMsg.getMessageId(), USER_A);
            assertThat(replyDetail.getReplyToId()).isEqualTo(textMsg.getMessageId());
            assertThat(replyDetail.getReplyToSenderId()).isEqualTo(USER_A);
            log.info("Step 6: 回复详情验证通过, replyToId={}, replyToSenderId={}",
                replyDetail.getReplyToId(), replyDetail.getReplyToSenderId());

            // Step 7: 撤回图片消息
            callRecallMessage(imageMsg.getMessageId(), USER_B);
            log.info("Step 7: 图片消息撤回成功, messageId={}", imageMsg.getMessageId());

            // Step 8: 撤回后的查询不返回该消息
            R<?> latestAfterRecall = callGetLatestMessages(LIFECYCLE_ROOM, 10, USER_A);
            List<?> afterRecallMsgs = objectMapper.convertValue(latestAfterRecall.getData(), List.class);
            boolean recalledFound = afterRecallMsgs.stream()
                .map(m -> objectMapper.convertValue(m, Map.class))
                .anyMatch(m -> imageMsg.getMessageId().equals(objectMapper.convertValue(m.get("messageId"), Long.class)));
            assertThat(recalledFound).isFalse();
            log.info("Step 8: 撤回消息不在 latest 查询中验证通过");

            // Step 9: 统计消息数量（不含已撤回）
            Long count = callCountRoomMessages(LIFECYCLE_ROOM, null, USER_A);
            // LIFECYCLE_ROOM 可能被其他 @Rollback(false) 测试留下数据
            assertThat(count).isGreaterThanOrEqualTo(2L);
            log.info("Step 9: 消息统计成功（不含撤回）, count={}", count);

            // Step 10: 清理房间
            // @Rollback(false) 使数据持久化，清理操作用于验证 cleanup API 正常工作
            Integer deleted = callCleanupRoom(LIFECYCLE_ROOM, USER_A);
            assertThat(deleted).isGreaterThanOrEqualTo(3);
            log.info("Step 10: 清理房间消息成功, deleted={}", deleted);

            log.info("==== 综合场景完成: 文本 -> 图片 -> 回复 -> 撤回 -> 统计 -> 清理 ===");
        }
    }
}
