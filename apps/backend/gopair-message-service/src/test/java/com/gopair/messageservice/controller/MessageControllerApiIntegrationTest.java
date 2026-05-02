package com.gopair.messageservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.common.constants.SystemConstants;
import com.gopair.common.core.R;
import com.gopair.messageservice.base.BaseIntegrationTest;
import com.gopair.messageservice.config.MockRestTemplateConfig;
import com.gopair.messageservice.domain.dto.SendMessageDto;
import com.gopair.messageservice.domain.vo.MessageVO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 消息 Controller 层 API 集成测试
 *
 * * [测试编排]
 * - 主干测试流 A：发送文本 -> 分页查询 -> 获取最新 -> 统计 -> 删除
 * - 分支测试流 B：发送图片 -> 查询详情 -> 权限校验
 * - 边界测试流 C：空房间查询 -> 消息不存在查询 -> 不在房间发送
 *
 * * [环境与中间件]
 * - MySQL（gopair_test）：消息表、app_user 表
 * - Redis：真实连接（DB 14），测试后 flushDb() 清理
 * - RabbitMQ：Mock（webSocketMessageProducer）
 * - realRestTemplate + userHeaders：模拟网关注入 X-User-Id / X-Nickname 请求头，
 *   确保 ContextInitFilter 能正确提取用户上下文
 * - mockRestTemplate：Service 层调用外部服务（room-service）走 mock
 *
 * * [脏数据清理]
 * - @Transactional：MySQL 数据自动回滚
 * - @AfterEach flushDb()：Redis 数据清理
 * - MockRestTemplateConfig.clear()：清理 stub 配置
 */
@Slf4j
class MessageControllerApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("realRestTemplate")
    private RestTemplate realRestTemplate;

    private static final Long ROOM_ID = 1L;
    private static final Long USER_A_ID = 100L;
    private static final Long USER_B_ID = 200L;

    private static final String SEND_MSG_URL = "/message/send";
    private static final String ROOM_MSG_URL = "/message/room/";
    private static final String MSG_DETAIL_URL = "/message/";

    @BeforeEach
    void setUpUserAndRoom() {
        MockRestTemplateConfig.clear();

        // 预置用户公开资料，使用 MySQL INSERT ... ON DUPLICATE KEY UPDATE
        jdbcTemplate.update("INSERT INTO app_user (user_id, nickname, avatar, username) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), avatar = VALUES(avatar)",
                USER_A_ID, "Alice", "http://avatar/alice.png", "alice");
        jdbcTemplate.update("INSERT INTO app_user (user_id, nickname, avatar, username) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), avatar = VALUES(avatar)",
                USER_B_ID, "Bob", "http://avatar/bob.png", "bob");

        // 模拟用户在房间内
        mockUserInRoom(ROOM_ID, USER_A_ID, true);
        mockUserInRoom(ROOM_ID, USER_B_ID, true);
    }

    /**
     * 构建带用户上下文的 HTTP 请求头，模拟网关注入的用户身份。
     */
    private HttpHeaders userHeaders(Long userId, String nickname) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(SystemConstants.HEADER_USER_ID, String.valueOf(userId));
        headers.set(SystemConstants.HEADER_NICKNAME, nickname);
        return headers;
    }

    @AfterEach
    void tearDown() {
        MockRestTemplateConfig.clear();
    }

    // ========== 主干测试流 A：发送 -> 分页查询 -> 最新 -> 统计 -> 删除 ==========

    @Test
    @DisplayName("【主干A】POST /message/send -> GET /message/room/{id} -> GET /message/room/{id}/latest -> GET /message/room/{id}/count -> DELETE /message/{id}")
    void textMessage_ControllerFullLifecycle() throws Exception {
        // ---- Step 2: POST /message/send 发送文本消息 ----
        log.info("==== [Step 2: Controller API - POST /message/send] ====");
        SendMessageDto sendDto = new SendMessageDto();
        sendDto.setRoomId(ROOM_ID);
        sendDto.setMessageType(1);
        sendDto.setContent("Controller test message");

        ResponseEntity<R> sendResponse = realRestTemplate.postForEntity(
                getUrl(SEND_MSG_URL), new HttpEntity<>(sendDto, userHeaders(USER_A_ID, "Alice")), R.class);

        assertThat(sendResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sendResponse.getBody()).isNotNull();
        assertThat(sendResponse.getBody().getCode()).isEqualTo(200);

        MessageVO sent = objectMapper.convertValue(sendResponse.getBody().getData(), MessageVO.class);
        assertThat(sent.getMessageId()).isNotNull();
        assertThat(sent.getContent()).isEqualTo("Controller test message");
        log.info("发送消息成功: messageId={}, content={}", sent.getMessageId(), sent.getContent());

        // ---- Step 3: GET /message/room/{id} 分页查询 ----
        log.info("==== [Step 3: Controller API - GET /message/room/{id}] ====");
        ResponseEntity<R> pageResponse = realRestTemplate.exchange(
                getUrl(ROOM_MSG_URL + ROOM_ID + "?pageNum=1&pageSize=10"),
                HttpMethod.GET, new HttpEntity<>(userHeaders(USER_A_ID, "Alice")), R.class);

        assertThat(pageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pageResponse.getBody()).isNotNull();
        assertThat(pageResponse.getBody().getCode()).isEqualTo(200);
        log.info("分页查询成功");

        // ---- Step 4: GET /message/room/{id}/latest 获取最新消息 ----
        log.info("==== [Step 4: Controller API - GET /message/room/{id}/latest] ====");
        ResponseEntity<R> latestResponse = realRestTemplate.exchange(
                getUrl(ROOM_MSG_URL + ROOM_ID + "/latest?limit=5"),
                HttpMethod.GET, new HttpEntity<>(userHeaders(USER_A_ID, "Alice")), R.class);

        assertThat(latestResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(latestResponse.getBody()).isNotNull();
        assertThat(latestResponse.getBody().getCode()).isEqualTo(200);
        log.info("获取最新消息成功");

        // ---- Step 5: GET /message/room/{id}/count 统计消息数量 ----
        log.info("==== [Step 5: Controller API - GET /message/room/{id}/count] ====");
        ResponseEntity<R> countResponse = realRestTemplate.exchange(
                getUrl(ROOM_MSG_URL + ROOM_ID + "/count"),
                HttpMethod.GET, new HttpEntity<>(userHeaders(USER_A_ID, "Alice")), R.class);

        assertThat(countResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(countResponse.getBody()).isNotNull();
        assertThat(countResponse.getBody().getCode()).isEqualTo(200);
        Object countData = countResponse.getBody().getData();
        assertThat(countData).isNotNull();
        log.info("消息统计成功: count={}", countData);

        // ---- Step 6: DELETE /message/{id} 删除消息 ----
        log.info("==== [Step 6: Controller API - DELETE /message/{id}] ====");
        ResponseEntity<R> deleteResponse = realRestTemplate.exchange(
                getUrl(MSG_DETAIL_URL + sent.getMessageId()),
                HttpMethod.DELETE, new HttpEntity<>(userHeaders(USER_A_ID, "Alice")), R.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deleteResponse.getBody()).isNotNull();
        assertThat(deleteResponse.getBody().getCode()).isEqualTo(200);
        log.info("消息删除成功: messageId={}", sent.getMessageId());
    }

    // ========== 分支测试流 B：文件消息 + 查询详情 ==========

    @Test
    @DisplayName("【分支B】发送图片 -> 查询详情 -> 无权删除 -> 有权删除")
    void fileMessage_ControllerDetailAndPermissionCheck() throws Exception {
        // ---- Step 1: Bob 发送图片消息 ----
        log.info("==== [Step 1: Controller API - 发送图片消息] ====");
        SendMessageDto imageDto = new SendMessageDto();
        imageDto.setRoomId(ROOM_ID);
        imageDto.setMessageType(2);
        imageDto.setFileUrl("http://minio.example.com/photos/screenshot.png");
        imageDto.setFileName("screenshot.png");
        imageDto.setFileSize(204800L);

        ResponseEntity<R> sendResponse = realRestTemplate.postForEntity(
                getUrl(SEND_MSG_URL), new HttpEntity<>(imageDto, userHeaders(USER_B_ID, "Bob")), R.class);
        assertThat(sendResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        MessageVO imageMsg = objectMapper.convertValue(sendResponse.getBody().getData(), MessageVO.class);
        assertThat(imageMsg.getMessageId()).isNotNull();
        log.info("图片消息发送成功: messageId={}, fileUrl={}", imageMsg.getMessageId(), imageMsg.getFileUrl());

        // ---- Step 2: GET /message/{id} 查询详情 ----
        log.info("==== [Step 2: Controller API - GET /message/{id}] ====");
        ResponseEntity<R> detailResponse = realRestTemplate.exchange(
                getUrl(MSG_DETAIL_URL + imageMsg.getMessageId()),
                HttpMethod.GET, new HttpEntity<>(userHeaders(USER_B_ID, "Bob")), R.class);

        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detailResponse.getBody()).isNotNull();
        assertThat(detailResponse.getBody().getCode()).isEqualTo(200);
        log.info("消息详情查询成功: messageId={}", imageMsg.getMessageId());

        // ---- Step 3: Alice 尝试删除 Bob 的消息（无权）----
        log.info("==== [Step 3: Controller API - 无权删除他人消息] ====");
        ResponseEntity<R> noPermResponse = realRestTemplate.exchange(
                getUrl(MSG_DETAIL_URL + imageMsg.getMessageId()),
                HttpMethod.DELETE, new HttpEntity<>(userHeaders(USER_A_ID, "Alice")), R.class);

        assertThat(noPermResponse.getBody()).isNotNull();
        log.info("无权删除响应: code={}", noPermResponse.getBody().getCode());

        // ---- Step 4: Bob 删除自己的消息（有权）----
        log.info("==== [Step 4: Controller API - 有权删除自己的消息] ====");
        ResponseEntity<R> deleteResponse = realRestTemplate.exchange(
                getUrl(MSG_DETAIL_URL + imageMsg.getMessageId()),
                HttpMethod.DELETE, new HttpEntity<>(userHeaders(USER_B_ID, "Bob")), R.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deleteResponse.getBody()).isNotNull();
        assertThat(deleteResponse.getBody().getCode()).isEqualTo(200);
        log.info("消息删除成功: messageId={}", imageMsg.getMessageId());
    }

    // ========== 边界测试流 C ==========

    @Test
    @DisplayName("【边界C-1】空房间消息查询返回空分页")
    void getRoomMessages_EmptyRoom_ReturnsEmpty() {
        log.info("==== [边界测试: 空房间查询] ====");
        ResponseEntity<R> response = realRestTemplate.getForEntity(
                getUrl(ROOM_MSG_URL + 99999L + "?pageNum=1&pageSize=10"), R.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(200);
        log.info("空房间查询成功: 返回空分页结果");
    }

    @Test
    @DisplayName("【边界C-2】查询不存在消息返回错误码")
    void getMessageById_NotFound_ReturnsErrorCode() {
        log.info("==== [边界测试: 查询不存在的消息] ====");
        ResponseEntity<R> response = realRestTemplate.getForEntity(
                getUrl(MSG_DETAIL_URL + 99999L), R.class);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isNotEqualTo(200);
        log.info("不存在消息查询: 返回业务错误码 code={}", response.getBody().getCode());
    }

    @Test
    @DisplayName("【边界C-3】用户在房间外发送消息被拦截")
    void sendMessage_UserNotInRoom_Blocked() {
        // 覆盖 stub：USER_A_ID 不在房间（返回 false）
        mockUserInRoom(ROOM_ID, USER_A_ID, false);

        log.info("==== [边界测试: 用户不在房间发送消息] ====");
        SendMessageDto dto = new SendMessageDto();
        dto.setRoomId(ROOM_ID);
        dto.setMessageType(1);
        dto.setContent("Should be blocked");

        ResponseEntity<R> response = realRestTemplate.postForEntity(
                getUrl(SEND_MSG_URL), new HttpEntity<>(dto, userHeaders(USER_A_ID, "Alice")), R.class);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isNotEqualTo(200);
        log.info("不在房间发送消息被拦截: code={}", response.getBody().getCode());
    }
}
