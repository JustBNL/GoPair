package com.gopair.messageservice.service;

import com.gopair.common.core.R;
import com.gopair.messageservice.base.BaseIntegrationTest;
import com.gopair.messageservice.config.MockRestTemplateConfig;
import com.gopair.messageservice.domain.dto.SendMessageDto;
import com.gopair.messageservice.domain.vo.MessageVO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserProfileFallbackService 三层降级链路集成测试
 *
 * * [三层降级策略]
 * - 第一层（主路径）：各查询方法通过 SQL JOIN user 表直接读出昵称/头像（已内嵌于 Mapper XML）。
 *   测试场景：Mapper JOIN 结果已有 nickname → 无需降级。
 * - 第二层（共享库降级）：若 JOIN 结果中昵称为空，聚合缺失 userId 后从同库 app_user 表补拉（IN 查询）。
 *   测试场景：Mapper JOIN 无 nickname → UserPublicMapper IN 查询补全。
 * - 第三层（HTTP 降级）：若共享库也无数据，调 user-service 批量 HTTP 再单个补拉。
 *   测试场景：Mapper JOIN + UserPublicMapper 均无数据 → RestTemplate HTTP 补全。
 *
 * * [环境]
 * - MySQL（gopair_test）：app_user 表，UserPublicMapper.selectByUserIds IN 查询。
 * - Redis：真实连接（DB 14），测试后 flushDb() 清理。
 * - RestTemplate：MockRestTemplateConfig（@Primary mock），测试方法内配置 stub。
 *
 * * [脏数据清理]
 * - @Transactional：MySQL 数据自动回滚。
 * - @AfterEach flushDb()：Redis 数据清理。
 * - MockRestTemplateConfig.clear()：清理 HTTP stub。
 */
@Slf4j
class UserProfileFallbackServiceImplIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserProfileFallbackService userProfileFallbackService;

    private static final Long ROOM_ID = 1L;
    private static final Long USER_A_ID = 100L;
    private static final Long USER_B_ID = 200L;
    private static final Long USER_C_ID = 300L;

    @BeforeEach
    void setUpUserProfiles() {
        MockRestTemplateConfig.clear();

        // 预置部分用户资料到 app_user 表（模拟第二层降级场景）
        // USER_A 和 USER_B 预置，USER_C 不预置（触发第三层 HTTP 降级）
        jdbcTemplate.update("INSERT INTO app_user (user_id, nickname, avatar, username) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), avatar = VALUES(avatar)",
                USER_A_ID, "AliceFromDB", "http://avatar/alice.png", "alice");
        jdbcTemplate.update("INSERT INTO app_user (user_id, nickname, avatar, username) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), avatar = VALUES(avatar)",
                USER_B_ID, "BobFromDB", "http://avatar/bob.png", "bob");
        // USER_C 不预置 → 触发第三层 HTTP 降级

        // 模拟用户在房间内（供事件测试使用）
        mockUserInRoom(ROOM_ID, USER_A_ID, true);
        mockUserInRoom(ROOM_ID, USER_B_ID, true);
        mockUserInRoom(ROOM_ID, USER_C_ID, true);
    }

    @AfterEach
    void tearDown() {
        MockRestTemplateConfig.clear();
    }

    // ========== 第一层测试：Mapper JOIN 已有昵称，无需降级 ==========

    @Test
    @DisplayName("【第一层】Mapper JOIN 结果已有 nickname → 不触发第二/三层降级")
    void mapperJoinHasNickname_NoFallback() {
        log.info("==== [第一层测试: Mapper JOIN 已有昵称] ====");

        // 构建消息列表，模拟 Mapper JOIN 后 senderNickname 已填充的场景
        MessageVO msg1 = buildMessageVO(1L, ROOM_ID, USER_A_ID, 1, "Hi", "Alice", null);
        MessageVO msg2 = buildMessageVO(2L, ROOM_ID, USER_B_ID, 1, "Hello", "Bob", null);

        List<MessageVO> messages = new ArrayList<>(List.of(msg1, msg2));

        // 调用降级服务（模拟 Mapper JOIN 后调用）
        userProfileFallbackService.fillMissingProfiles(messages, null);

        // 验证：昵称保持不变，未触发任何降级逻辑
        assertThat(messages.get(0).getSenderNickname()).isEqualTo("Alice");
        assertThat(messages.get(1).getSenderNickname()).isEqualTo("Bob");

        // RestTemplate 未被调用（无降级发生）
        log.info("第一层测试通过: Mapper JOIN 已有昵称，未触发降级");
    }

    // ========== 第二层测试：Mapper JOIN 无昵称，UserPublicMapper IN 查询补全 ==========

    @Test
    @DisplayName("【第二层】Mapper JOIN 无 nickname → UserPublicMapper IN 查询补全")
    void mapperJoinEmpty_SharedTableFallback() {
        log.info("==== [第二层测试: 共享库 app_user 表 IN 查询补全] ====");

        // 构建消息列表，模拟 Mapper JOIN 后 senderNickname 为空
        MessageVO msg1 = buildMessageVO(3L, ROOM_ID, USER_A_ID, 1, "Hello", null, null);
        MessageVO msg2 = buildMessageVO(4L, ROOM_ID, USER_B_ID, 1, "Hi", null, null);

        List<MessageVO> messages = new ArrayList<>(List.of(msg1, msg2));

        // 调用降级服务 → 应触发第二层，从 app_user 表补拉
        userProfileFallbackService.fillMissingProfiles(messages, null);

        // 验证：昵称从 app_user 表补全（USER_A → AliceFromDB，USER_B → BobFromDB）
        assertThat(messages.get(0).getSenderNickname()).isEqualTo("AliceFromDB");
        assertThat(messages.get(0).getSenderAvatar()).isEqualTo("http://avatar/alice.png");
        assertThat(messages.get(1).getSenderNickname()).isEqualTo("BobFromDB");
        assertThat(messages.get(1).getSenderAvatar()).isEqualTo("http://avatar/bob.png");

        log.info("第二层测试通过: 共享库 IN 查询补全");
    }

    // ========== 第三层测试：Mapper + 共享库均无数据，RestTemplate HTTP 补全 ==========

    @Test
    @DisplayName("【第三层】共享库无数据 → RestTemplate 批量 + 单个 HTTP 补全")
    void allLocalFallbackFailed_HttpFallback() {
        log.info("==== [第三层测试: HTTP 降级补全] ====");

        // 模拟 USER_C 在 app_user 表中无数据，触发第三层降级
        // 配置 RestTemplate stub：批量接口返回 USER_C 的数据
        String batchUrl = "http://user-service/user/by-ids?ids=" + USER_C_ID;
        String batchResponse = "{\"code\":200,\"data\":[{\"userId\":" + USER_C_ID + ",\"nickname\":\"CharlieFromHttp\",\"avatar\":\"http://avatar/charlie.png\"}]}";
        MockRestTemplateConfig.putHttpStub(batchUrl, batchResponse);

        // 构建消息列表，senderId 为 USER_C（共享库无数据）
        MessageVO msg = buildMessageVO(5L, ROOM_ID, USER_C_ID, 1, "From HTTP", null, null);
        List<MessageVO> messages = new ArrayList<>(List.of(msg));

        // 调用降级服务 → 第二层无数据 → 第三层 HTTP 补全
        userProfileFallbackService.fillMissingProfiles(messages, null);

        // 验证：昵称从 RestTemplate HTTP 响应补全
        assertThat(messages.get(0).getSenderNickname()).isEqualTo("CharlieFromHttp");
        assertThat(messages.get(0).getSenderAvatar()).isEqualTo("http://avatar/charlie.png");

        log.info("第三层测试通过: HTTP 批量接口被调用，昵称从响应补全: {}",
                messages.get(0).getSenderNickname());
    }

    // ========== 混合场景：部分第一层 + 部分第三层 ==========

    @Test
    @DisplayName("【混合】部分消息已填充，部分需第三层降级")
    void mixedProfiles_PartialFallback() {
        log.info("==== [混合场景测试: 部分已有昵称，部分需 HTTP 补全] ====");

        // 配置 USER_C 的 HTTP stub
        String batchUrl = "http://user-service/user/by-ids?ids=" + USER_C_ID;
        String batchResponse = "{\"code\":200,\"data\":[{\"userId\":" + USER_C_ID + ",\"nickname\":\"CharlieHttp\",\"avatar\":\"http://avatar/charlie_http.png\"}]}";
        MockRestTemplateConfig.putHttpStub(batchUrl, batchResponse);

        // 构建混合消息列表：USER_A 有昵称（第一层），USER_C 无昵称（第三层）
        MessageVO msgA = buildMessageVO(6L, ROOM_ID, USER_A_ID, 1, "Hello", "AliceAlready", null);
        MessageVO msgC = buildMessageVO(7L, ROOM_ID, USER_C_ID, 1, "Hi", null, null);

        List<MessageVO> messages = new ArrayList<>(List.of(msgA, msgC));

        userProfileFallbackService.fillMissingProfiles(messages, null);

        // USER_A 保持原有昵称
        assertThat(messages.get(0).getSenderNickname()).isEqualTo("AliceAlready");
        // USER_C 从 HTTP 补全
        assertThat(messages.get(1).getSenderNickname()).isEqualTo("CharlieHttp");
        assertThat(messages.get(1).getSenderAvatar()).isEqualTo("http://avatar/charlie_http.png");

        log.info("混合场景测试通过: USER_A={}, USER_C={}",
                messages.get(0).getSenderNickname(), messages.get(1).getSenderNickname());
    }

    // ========== 边界：空消息列表 ==========

    @Test
    @DisplayName("【边界】空消息列表 → 直接返回，不抛异常")
    void emptyMessageList_NoException() {
        log.info("==== [边界测试: 空消息列表] ====");
        List<MessageVO> empty = new ArrayList<>();

        // 不抛异常，正常返回
        userProfileFallbackService.fillMissingProfiles(empty, null);

        log.info("边界测试通过: 空列表处理正常");
    }

    // ========== 辅助方法 ==========

    private MessageVO buildMessageVO(Long messageId, Long roomId, Long senderId,
                                     Integer messageType, String content,
                                     String senderNickname, String senderAvatar) {
        MessageVO vo = new MessageVO();
        vo.setMessageId(messageId);
        vo.setRoomId(roomId);
        vo.setSenderId(senderId);
        vo.setMessageType(messageType);
        vo.setContent(content);
        vo.setSenderNickname(senderNickname);
        vo.setSenderAvatar(senderAvatar);
        vo.setCreateTime(LocalDateTime.now());
        vo.setUpdateTime(LocalDateTime.now());
        return vo;
    }
}
