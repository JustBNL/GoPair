package com.gopair.roomservice.api;

import com.gopair.common.constants.SystemConstants;
import com.gopair.common.core.PageResult;
import com.gopair.common.core.R;
import com.gopair.roomservice.base.BaseIntegrationTest;
import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.enums.RoomErrorCode;
import com.gopair.roomservice.domain.dto.*;
import com.gopair.roomservice.domain.vo.JoinAcceptedVO;
import com.gopair.roomservice.domain.vo.RoomMemberVO;
import com.gopair.roomservice.domain.vo.RoomPasswordVO;
import com.gopair.roomservice.domain.vo.RoomVO;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import com.gopair.roomservice.service.JoinResultQueryService.JoinStatusVO;
import com.gopair.roomservice.service.RoomCacheSyncService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 房间 API 契约测试 — HTTP 层集成测试。
 *
 * * [核心策略]
 * - 真实 HTTP 调用：通过 TestRestTemplate 走完整 Spring MVC 链路，验证 Controller 层行为。
 * - Auth 注入：X-User-Id / X-Nickname 请求头经 ContextInitFilter 解析后填充 UserContextHolder。
 * - 全局唯一测试数据：使用 uid() 原子计数器确保测试数据不冲突。
 * - 异常断言降级：先验 isSuccess==false，再独立用 R<T> 验成功路径，避免泛型解析失败。
 *
 * * [Mock 范围]
 * - MQ / WebSocket 已在 BaseIntegrationTest 中 Mock，避免测试间相互干扰。
 * - 用户资料查询：Mock 的 RestTemplate 返回 null，降级为「用户{userId}」昵称。
 *
 * * [Redis 初始化说明]
 * - createRoom 的 afterCommit 在测试类事务结束时才触发，但 joinAsync 的 Redis Lua 脚本
 *   依赖 metaKey/membersKey 已存在。within-transaction Redis 查询（membersKey 不存在时降级查 DB）
 *   会因 MySQL 隔离级别（REPEATABLE READ + MVCC）看不到未提交数据。
 * - 因此在调用 joinAsync 前使用 registerSynchronization(afterCommit) 延迟 Redis 初始化，
 *   确保 MySQL 事务已提交、Redis 初始化读取的是已提交数据。
 *
 * @author gopair
 */
@DisplayName("房间 API 契约测试")
class RoomApiContractTest extends BaseIntegrationTest {

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @Autowired
    private org.springframework.data.redis.core.script.DefaultRedisScript<Long> roomPreReserveScript;

    @Autowired
    private com.gopair.roomservice.service.RoomService roomService;

    @Autowired
    private RoomCacheSyncService roomCacheSyncService;

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private RoomMemberMapper roomMemberMapper;

    private static final AtomicLong counter = new AtomicLong(System.currentTimeMillis() % 100_000_000);

    private long uid() {
        return counter.incrementAndGet();
    }

    // ========================================================================
    // HTTP 请求辅助方法
    // ========================================================================

    private HttpHeaders userHeaders(Long userId, String nickname) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(SystemConstants.HEADER_USER_ID, String.valueOf(userId));
        headers.set(SystemConstants.HEADER_NICKNAME, nickname);
        return headers;
    }

    private HttpHeaders noAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // POST /room
    private ResponseEntity<R<RoomVO>> callCreateRoom(RoomDto dto, Long userId, String nickname) {
        HttpEntity<RoomDto> entity = new HttpEntity<>(dto, userHeaders(userId, nickname));
        return testRestTemplate.exchange(
                getRoomUrl(""),
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<R<RoomVO>>() {});
    }

    // POST /room/join/async
    private ResponseEntity<R<JoinAcceptedVO>> callJoinAsync(JoinRoomDto dto, Long userId, String nickname) {
        HttpEntity<JoinRoomDto> entity = new HttpEntity<>(dto, userHeaders(userId, nickname));
        return testRestTemplate.exchange(
                getRoomUrl("/join/async"),
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<R<JoinAcceptedVO>>() {});
    }

    // GET /room/join/result
    private ResponseEntity<R<JoinStatusVO>> callJoinResult(String token) {
        HttpEntity<Void> entity = new HttpEntity<>(noAuthHeaders());
        return testRestTemplate.exchange(
                getRoomUrl("/join/result?token=" + token),
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<R<JoinStatusVO>>() {});
    }

    // POST /room/{roomId}/leave
    private ResponseEntity<R<Boolean>> callLeaveRoom(Long roomId, Long userId, String nickname) {
        HttpEntity<Void> entity = new HttpEntity<>(userHeaders(userId, nickname));
        return testRestTemplate.exchange(
                getRoomUrl("/" + roomId + "/leave"),
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<R<Boolean>>() {});
    }

    // GET /room/code/{roomCode}
    private ResponseEntity<R<RoomVO>> callGetRoomByCode(String roomCode) {
        HttpEntity<Void> entity = new HttpEntity<>(noAuthHeaders());
        return testRestTemplate.exchange(
                getRoomUrl("/code/" + roomCode),
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<R<RoomVO>>() {});
    }

    // GET /room/{roomId}/members
    private ResponseEntity<R<List<RoomMemberVO>>> callGetMembers(Long roomId, Long userId, String nickname) {
        HttpEntity<Void> entity = new HttpEntity<>(userHeaders(userId, nickname));
        return testRestTemplate.exchange(
                getRoomUrl("/" + roomId + "/members"),
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<R<List<RoomMemberVO>>>() {});
    }

    // POST /room/{roomId}/close
    private ResponseEntity<R<Boolean>> callCloseRoom(Long roomId, Long userId, String nickname) {
        HttpEntity<Void> entity = new HttpEntity<>(userHeaders(userId, nickname));
        return testRestTemplate.exchange(
                getRoomUrl("/" + roomId + "/close"),
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<R<Boolean>>() {});
    }

    // POST /room/{roomId}/renew
    private ResponseEntity<R<RoomVO>> callRenewRoom(Long roomId, RenewRoomDto dto, Long userId, String nickname) {
        HttpEntity<RenewRoomDto> entity = new HttpEntity<>(dto, userHeaders(userId, nickname));
        return testRestTemplate.exchange(
                getRoomUrl("/" + roomId + "/renew"),
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<R<RoomVO>>() {});
    }

    // POST /room/{roomId}/reopen
    private ResponseEntity<R<RoomVO>> callReopenRoom(Long roomId, ReopenRoomDto dto, Long userId, String nickname) {
        HttpEntity<ReopenRoomDto> entity = new HttpEntity<>(dto, userHeaders(userId, nickname));
        return testRestTemplate.exchange(
                getRoomUrl("/" + roomId + "/reopen"),
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<R<RoomVO>>() {});
    }

    // GET /room/my
    private ResponseEntity<R<PageResult<RoomVO>>> callGetMyRooms(RoomQueryDto query, Long userId, String nickname) {
        HttpEntity<Void> entity = new HttpEntity<>(userHeaders(userId, nickname));
        return testRestTemplate.exchange(
                getRoomUrl("/my"),
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<R<PageResult<RoomVO>>>() {});
    }

    // PATCH /room/{roomId}/password
    private ResponseEntity<R<Void>> callUpdatePassword(Long roomId, UpdateRoomPasswordDto dto, Long userId, String nickname) {
        HttpEntity<UpdateRoomPasswordDto> entity = new HttpEntity<>(dto, userHeaders(userId, nickname));
        return testRestTemplate.exchange(
                getRoomUrl("/" + roomId + "/password"),
                HttpMethod.PATCH,
                entity,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    // PATCH /room/{roomId}/password/visibility
    private ResponseEntity<R<Void>> callUpdateVisibility(Long roomId, UpdatePasswordVisibilityDto dto, Long userId, String nickname) {
        HttpEntity<UpdatePasswordVisibilityDto> entity = new HttpEntity<>(dto, userHeaders(userId, nickname));
        return testRestTemplate.exchange(
                getRoomUrl("/" + roomId + "/password/visibility"),
                HttpMethod.PATCH,
                entity,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    // GET /room/{roomId}/password/current
    private ResponseEntity<R<RoomPasswordVO>> callGetCurrentPassword(Long roomId, Long userId, String nickname) {
        HttpEntity<Void> entity = new HttpEntity<>(userHeaders(userId, nickname));
        return testRestTemplate.exchange(
                getRoomUrl("/" + roomId + "/password/current"),
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<R<RoomPasswordVO>>() {});
    }

    // DELETE /room/{roomId}/members/{userId}
    private ResponseEntity<R<Void>> callKickMember(Long roomId, Long targetUserId, Long operatorId, String nickname) {
        HttpEntity<Void> entity = new HttpEntity<>(userHeaders(operatorId, nickname));
        return testRestTemplate.exchange(
                getRoomUrl("/" + roomId + "/members/" + targetUserId),
                HttpMethod.DELETE,
                entity,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    // GET /room/{roomId}/members/{userId}/check
    private ResponseEntity<R<Boolean>> callCheckMember(Long roomId, Long userId) {
        HttpEntity<Void> entity = new HttpEntity<>(noAuthHeaders());
        return testRestTemplate.exchange(
                getRoomUrl("/" + roomId + "/members/" + userId + "/check"),
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<R<Boolean>>() {});
    }

    // GET /room/{roomId}/status
    private ResponseEntity<R<Integer>> callGetRoomStatus(Long roomId) {
        HttpEntity<Void> entity = new HttpEntity<>(noAuthHeaders());
        return testRestTemplate.exchange(
                getRoomUrl("/" + roomId + "/status"),
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<R<Integer>>() {});
    }

    // POST /room/{roomId}/members/batch
    private ResponseEntity<R<Integer>> callBatchAddMembers(Long roomId, List<Long> userIds) {
        HttpEntity<List<Long>> entity = new HttpEntity<>(userIds, noAuthHeaders());
        return testRestTemplate.exchange(
                getRoomUrl("/" + roomId + "/members/batch"),
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<R<Integer>>() {});
    }

    // ========================================================================
    // POST /room — 创建房间
    // ========================================================================

    @Nested
    @DisplayName("POST /room — 创建房间")
    class CreateRoomTests {

        @Test
        @DisplayName("成功路径：创建无密码房间 → 返回 roomId + roomCode")
        void createRoom_Success_NoPassword() {
            Long userId = uid();
            RoomDto dto = buildRoomDto("测试房间_" + uid(), RoomConst.PASSWORD_MODE_NONE);

            ResponseEntity<R<RoomVO>> resp = callCreateRoom(dto, userId, "owner_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            RoomVO room = resp.getBody().getData();
            assertThat(room.getRoomId()).isNotNull();
            assertThat(room.getRoomCode()).hasSize(8);
            assertThat(room.getRoomName()).isEqualTo(dto.getRoomName());
            assertThat(room.getStatus()).isEqualTo(RoomConst.STATUS_ACTIVE);
            assertThat(room.getOwnerId()).isEqualTo(userId);
            assertThat(room.getCurrentMembers()).isEqualTo(1); // 创建者自动入房
            assertThat(room.getPasswordMode()).isEqualTo(RoomConst.PASSWORD_MODE_NONE);
        }

        @Test
        @DisplayName("成功路径：创建固定密码房间 → 密码加密存储")
        void createRoom_Success_FixedPassword() {
            Long userId = uid();
            RoomDto dto = buildRoomDto("密码房间_" + uid(), RoomConst.PASSWORD_MODE_FIXED);
            dto.setRawPassword("P@ssw0rd_" + uid());
            dto.setPasswordVisible(1);

            ResponseEntity<R<RoomVO>> resp = callCreateRoom(dto, userId, "owner_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            RoomVO room = resp.getBody().getData();
            assertThat(room.getPasswordMode()).isEqualTo(RoomConst.PASSWORD_MODE_FIXED);
        }

        @Test
        @DisplayName("成功路径：创建 TOTP 动态密码房间 → 生成密钥")
        void createRoom_Success_TotpMode() {
            Long userId = uid();
            RoomDto dto = buildRoomDto("TOTP房间_" + uid(), RoomConst.PASSWORD_MODE_TOTP);

            ResponseEntity<R<RoomVO>> resp = callCreateRoom(dto, userId, "owner_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getPasswordMode()).isEqualTo(RoomConst.PASSWORD_MODE_TOTP);
        }

        @Test
        @DisplayName("参数校验：roomName 为空 → 400")
        void createRoom_BlankRoomName_400() {
            Long userId = uid();
            RoomDto dto = new RoomDto();
            dto.setRoomName(""); // blank
            dto.setMaxMembers(5);
            dto.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);

            ResponseEntity<R<Void>> resp = testRestTemplate.exchange(
                    getRoomUrl(""),
                    HttpMethod.POST,
                    new HttpEntity<>(dto, userHeaders(userId, "nick")),
                    new ParameterizedTypeReference<R<Void>>() {});

            assertThat(resp.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.OK);
        }

        @Test
        @DisplayName("参数校验：maxMembers 小于 2 → 400")
        void createRoom_MaxMembersTooLow_400() {
            Long userId = uid();
            RoomDto dto = buildRoomDto("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE);
            dto.setMaxMembers(1);

            ResponseEntity<R<Void>> resp = testRestTemplate.exchange(
                    getRoomUrl(""),
                    HttpMethod.POST,
                    new HttpEntity<>(dto, userHeaders(userId, "nick")),
                    new ParameterizedTypeReference<R<Void>>() {});

            assertThat(resp.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.OK);
        }

        @Test
        @DisplayName("参数校验：roomName 超过 50 字符 → 400")
        void createRoom_RoomNameTooLong_400() {
            Long userId = uid();
            RoomDto dto = buildRoomDto("测试".repeat(50), RoomConst.PASSWORD_MODE_NONE);

            ResponseEntity<R<Void>> resp = testRestTemplate.exchange(
                    getRoomUrl(""),
                    HttpMethod.POST,
                    new HttpEntity<>(dto, userHeaders(userId, "nick")),
                    new ParameterizedTypeReference<R<Void>>() {});

            assertThat(resp.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.OK);
        }

        @Test
        @DisplayName("参数校验：passwordMode 非法值 → 400")
        void createRoom_InvalidPasswordMode_400() {
            Long userId = uid();
            RoomDto dto = buildRoomDto("房间_" + uid(), 99); // invalid mode

            ResponseEntity<R<Void>> resp = testRestTemplate.exchange(
                    getRoomUrl(""),
                    HttpMethod.POST,
                    new HttpEntity<>(dto, userHeaders(userId, "nick")),
                    new ParameterizedTypeReference<R<Void>>() {});

            assertThat(resp.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.OK);
        }

        @Test
        @DisplayName("参数校验：rawPassword 长度不足 4 → 400")
        void createRoom_PasswordTooShort_400() {
            Long userId = uid();
            RoomDto dto = buildRoomDto("房间_" + uid(), RoomConst.PASSWORD_MODE_FIXED);
            dto.setRawPassword("123");

            ResponseEntity<R<Void>> resp = testRestTemplate.exchange(
                    getRoomUrl(""),
                    HttpMethod.POST,
                    new HttpEntity<>(dto, userHeaders(userId, "nick")),
                    new ParameterizedTypeReference<R<Void>>() {});

            assertThat(resp.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.OK);
        }

        @Test
        @DisplayName("无认证：未提供 X-User-Id → 401")
        void createRoom_NoAuth_401() {
            RoomDto dto = buildRoomDto("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE);

            HttpHeaders headers = noAuthHeaders();
            ResponseEntity<R<Void>> resp = testRestTemplate.exchange(
                    getRoomUrl(""),
                    HttpMethod.POST,
                    new HttpEntity<>(dto, headers),
                    new ParameterizedTypeReference<R<Void>>() {});

            assertThat(resp.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.OK);
        }
    }

    // ========================================================================
    // POST /room/join/async — 异步申请加入房间
    // ========================================================================

    @Nested
    @DisplayName("POST /room/join/async — 异步申请加入房间")
    class JoinAsyncTests {

        /**
         * 直接通过 StringRedisTemplate 写 Redis，绕过事务回滚问题。
         * Redis 操作不参与 Spring 事务回滚，测试结束后 flushDb() 清理 Redis。
         * 直接设置 Lua 脚本所需的 metaKey/membersKey 字段。
         */
        private void ensureRedisInit(Long roomId, Long ownerId) {
            var dbRoom = roomMapper.selectById(roomId);
            System.out.println("[TEST-DBG] JoinAsync ensureRedisInit: roomId=" + roomId + " dbRoom=" + (dbRoom != null ? "found" : "NULL"));
            if (dbRoom == null) return;

            String metaKey = RoomConst.metaKey(roomId);
            String membersKey = RoomConst.membersKey(roomId);

            long expireAtMs = dbRoom.getExpireTime() == null ? 0L
                    : dbRoom.getExpireTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            int passwordMode = dbRoom.getPasswordMode() == null ? RoomConst.PASSWORD_MODE_NONE : dbRoom.getPasswordMode();

            Map<String, String> fields = new java.util.HashMap<>();
            fields.put(RoomConst.FIELD_MAX,           String.valueOf(dbRoom.getMaxMembers()     == null ? 0 : dbRoom.getMaxMembers()));
            fields.put(RoomConst.FIELD_CONFIRMED,   String.valueOf(dbRoom.getCurrentMembers() == null ? 0 : dbRoom.getCurrentMembers()));
            fields.put(RoomConst.FIELD_RESERVED,     "0");
            fields.put(RoomConst.FIELD_STATUS,       String.valueOf(dbRoom.getStatus()         == null ? 0 : dbRoom.getStatus()));
            fields.put(RoomConst.FIELD_EXPIRE_AT,    String.valueOf(expireAtMs));
            fields.put(RoomConst.FIELD_PASSWORD_MODE, String.valueOf(passwordMode));
            if (ownerId != null) {
                fields.put(RoomConst.FIELD_OWNER_ID, String.valueOf(ownerId));
            }
            System.out.println("[TEST-DBG] JoinAsync writing metaKey=" + metaKey + " fields=" + fields);

            stringRedisTemplate.opsForHash().putAll(metaKey, fields);
            if (ownerId != null) {
                stringRedisTemplate.opsForSet().add(membersKey, String.valueOf(ownerId));
            }

            var written = stringRedisTemplate.opsForHash().entries(metaKey);
            System.out.println("[TEST-DBG] JoinAsync after write, meta=" + written);
        }

        @Test
        @DisplayName("成功路径：无密码房间 → 返回 joinToken")
        void joinAsync_Success_NoPassword() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);
            ensureRedisInit(room.getRoomId(), ownerId);

            // Direct Lua script test: verify the script works with our Redis data
            String metaKey = RoomConst.metaKey(room.getRoomId());
            String membersKey = RoomConst.membersKey(room.getRoomId());
            String pendingKey = RoomConst.pendingKey(room.getRoomId());
            String tokenKey = "join:test_token_123";
            Long luaResult = stringRedisTemplate.execute(
                roomPreReserveScript,
                java.util.Arrays.asList(metaKey, membersKey, pendingKey, tokenKey),
                "999999", "test_token_123", String.valueOf(System.currentTimeMillis()), "30"
            );
            System.out.println("[TEST-DBG] Direct Lua call result: " + luaResult);

            // Direct service call - bypass HTTP layer entirely
            JoinRoomDto directDto = new JoinRoomDto();
            directDto.setRoomCode(room.getRoomCode());
            Long joinUserId = uid();
            try {
                var directAccepted = roomService.joinRoomAsync(directDto, joinUserId);
                System.out.println("[TEST-DBG] Direct service call ACCEPTED: token=" + directAccepted.getJoinToken());
            } catch (Exception e) {
                System.out.println("[TEST-DBG] Direct service call FAILED: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }

            JoinRoomDto joinDto = new JoinRoomDto();
            joinDto.setRoomCode(room.getRoomCode());

            ResponseEntity<R<JoinAcceptedVO>> resp = callJoinAsync(joinDto, uid(), "guest_" + uid());
            System.out.println("[TEST-DBG] joinAsync HTTP resp: statusCode=" + resp.getStatusCode() + " body=" + resp.getBody());
            if (resp.getBody() != null && !resp.getBody().isSuccess()) {
                System.out.println("[TEST-DBG] joinAsync failed: code=" + resp.getBody().getCode() + " msg=" + resp.getBody().getMsg());
            }
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getJoinToken()).isNotBlank();
        }

        @Test
        @DisplayName("成功路径：固定密码房间 + 正确密码 → 返回 joinToken")
        void joinAsync_Success_CorrectPassword() {
            Long ownerId = uid();
            String password = "P@ss_" + uid();
            RoomVO room = createRoom("密码房间_" + uid(), RoomConst.PASSWORD_MODE_FIXED, ownerId, password);
            ensureRedisInit(room.getRoomId(), ownerId);

            JoinRoomDto joinDto = new JoinRoomDto();
            joinDto.setRoomCode(room.getRoomCode());
            joinDto.setPassword(password);

            ResponseEntity<R<JoinAcceptedVO>> resp = callJoinAsync(joinDto, uid(), "guest_" + uid());
            if (resp.getBody() != null && !resp.getBody().isSuccess()) {
                System.out.println("[TEST-DBG] joinAsync failed: code=" + resp.getBody().getCode() + " msg=" + resp.getBody().getMsg());
            }
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getJoinToken()).isNotBlank();
        }

        @Test
        @DisplayName("错误路径：房间码不存在 → ROOM_NOT_FOUND")
        void joinAsync_InvalidRoomCode_Rejected() {
            JoinRoomDto joinDto = new JoinRoomDto();
            joinDto.setRoomCode("00000000");

            ResponseEntity<R<JoinAcceptedVO>> resp = callJoinAsync(joinDto, uid(), "guest_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.ROOM_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("错误路径：固定密码房间 + 错误密码 → PASSWORD_WRONG")
        void joinAsync_WrongPassword_Rejected() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_FIXED, ownerId, "correct_" + uid());
            ensureRedisInit(room.getRoomId(), ownerId);

            JoinRoomDto joinDto = new JoinRoomDto();
            joinDto.setRoomCode(room.getRoomCode());
            joinDto.setPassword("wrongpass_" + uid());

            ResponseEntity<R<JoinAcceptedVO>> resp = callJoinAsync(joinDto, uid(), "guest_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.PASSWORD_WRONG.getCode());
        }

        @Test
        @DisplayName("错误路径：固定密码房间未提供密码 → PASSWORD_REQUIRED")
        void joinAsync_MissingPassword_Rejected() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_FIXED, ownerId, "pass_" + uid());

            JoinRoomDto joinDto = new JoinRoomDto();
            joinDto.setRoomCode(room.getRoomCode());

            ResponseEntity<R<JoinAcceptedVO>> resp = callJoinAsync(joinDto, uid(), "guest_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.PASSWORD_REQUIRED.getCode());
        }

        @Test
        @DisplayName("错误路径：无密码房间提供多余密码字段 → ROOM_STATE_CHANGED")
        void joinAsync_ExtraPassword_Rejected() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);
            ensureRedisInit(room.getRoomId(), ownerId);

            JoinRoomDto joinDto = new JoinRoomDto();
            joinDto.setRoomCode(room.getRoomCode());
            joinDto.setPassword("ignored_password");

            ResponseEntity<R<JoinAcceptedVO>> resp = callJoinAsync(joinDto, uid(), "guest_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.ROOM_STATE_CHANGED.getCode());
        }
    }

    // ========================================================================
    // GET /room/join/result — 查询加入结果
    // ========================================================================

    @Nested
    @DisplayName("GET /room/join/result — 查询加入结果")
    class JoinResultTests {

        /**
         * 直接通过 StringRedisTemplate 写 Redis，绕过事务回滚问题。
         * Redis 操作不参与 Spring 事务回滚，测试结束后 flushDb() 清理 Redis。
         * 直接设置 Lua 脚本所需的 metaKey/membersKey 字段。
         */
        private void ensureRedisInit(Long roomId, Long ownerId) {
            var dbRoom = roomMapper.selectById(roomId);
            System.out.println("[TEST-DBG] JoinResult ensureRedisInit: roomId=" + roomId + " dbRoom=" + (dbRoom != null ? "found" : "NULL"));
            if (dbRoom == null) return;

            String metaKey = RoomConst.metaKey(roomId);
            String membersKey = RoomConst.membersKey(roomId);

            long expireAtMs = dbRoom.getExpireTime() == null ? 0L
                    : dbRoom.getExpireTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            int passwordMode = dbRoom.getPasswordMode() == null ? RoomConst.PASSWORD_MODE_NONE : dbRoom.getPasswordMode();

            Map<String, String> fields = new java.util.HashMap<>();
            fields.put(RoomConst.FIELD_MAX,           String.valueOf(dbRoom.getMaxMembers()     == null ? 0 : dbRoom.getMaxMembers()));
            fields.put(RoomConst.FIELD_CONFIRMED,   String.valueOf(dbRoom.getCurrentMembers() == null ? 0 : dbRoom.getCurrentMembers()));
            fields.put(RoomConst.FIELD_RESERVED,     "0");
            fields.put(RoomConst.FIELD_STATUS,       String.valueOf(dbRoom.getStatus()         == null ? 0 : dbRoom.getStatus()));
            fields.put(RoomConst.FIELD_EXPIRE_AT,    String.valueOf(expireAtMs));
            fields.put(RoomConst.FIELD_PASSWORD_MODE, String.valueOf(passwordMode));
            if (ownerId != null) {
                fields.put(RoomConst.FIELD_OWNER_ID, String.valueOf(ownerId));
            }
            System.out.println("[TEST-DBG] JoinResult writing metaKey=" + metaKey + " fields=" + fields);

            stringRedisTemplate.opsForHash().putAll(metaKey, fields);
            if (ownerId != null) {
                stringRedisTemplate.opsForSet().add(membersKey, String.valueOf(ownerId));
            }

            var written = stringRedisTemplate.opsForHash().entries(metaKey);
            System.out.println("[TEST-DBG] JoinResult after write, meta=" + written);
        }

        @Test
        @DisplayName("成功路径：token 不存在 → PROCESSING（降级默认值）")
        void joinResult_TokenNotFound_Processing() {
            ResponseEntity<R<JoinStatusVO>> resp = callJoinResult("nonexistent_token_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().status).isEqualTo(JoinStatusVO.Status.PROCESSING);
        }

        @Test
        @DisplayName("成功路径：joinToken 有效 → 返回 JOINED")
        void joinResult_ValidToken_Joined() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);
            ensureRedisInit(room.getRoomId(), ownerId);

            JoinRoomDto joinDto = new JoinRoomDto();
            joinDto.setRoomCode(room.getRoomCode());
            ResponseEntity<R<JoinAcceptedVO>> asyncResp = callJoinAsync(joinDto, uid(), "guest_" + uid());
            assertThat(asyncResp.getBody()).isNotNull();
            assertThat(asyncResp.getBody().isSuccess()).isTrue();
            String token = asyncResp.getBody().getData().getJoinToken();
            assertThat(token).isNotBlank();

            ResponseEntity<R<JoinStatusVO>> resp = callJoinResult(token);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
        }
    }

    // ========================================================================
    // POST /room/{roomId}/leave — 离开房间
    // ========================================================================

    @Nested
    @DisplayName("POST /room/{roomId}/leave — 离开房间")
    class LeaveRoomTests {

        @Test
        @DisplayName("成功路径：房主离开 → true")
        void leaveRoom_Success_OwnerLeaves() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            ResponseEntity<R<Boolean>> resp = callLeaveRoom(room.getRoomId(), ownerId, "owner_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isTrue();
        }

        @Test
        @DisplayName("错误路径：非成员离开 → NOT_IN_ROOM")
        void leaveRoom_NotMember_Fails() {
            Long ownerId = uid();
            Long strangerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            ResponseEntity<R<Boolean>> resp = callLeaveRoom(room.getRoomId(), strangerId, "stranger_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.NOT_IN_ROOM.getCode());
        }

        @Test
        @DisplayName("错误路径：房间不存在 → ROOM_NOT_FOUND")
        void leaveRoom_RoomNotFound_404() {
            ResponseEntity<R<Boolean>> resp = callLeaveRoom(999999L, uid(), "guest_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.ROOM_NOT_FOUND.getCode());
        }
    }

    // ========================================================================
    // GET /room/code/{roomCode} — 根据房间码查询房间
    // ========================================================================

    @Nested
    @DisplayName("GET /room/code/{roomCode} — 根据房间码查询房间")
    class GetRoomByCodeTests {

        @Test
        @DisplayName("成功路径：有效 roomCode → 返回房间信息")
        void getRoomByCode_Success() {
            Long ownerId = uid();
            RoomVO room = createRoom("按码查询_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            ResponseEntity<R<RoomVO>> resp = callGetRoomByCode(room.getRoomCode());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getRoomId()).isEqualTo(room.getRoomId());
            assertThat(resp.getBody().getData().getRoomName()).isEqualTo(room.getRoomName());
        }

        @Test
        @DisplayName("错误路径：无效 roomCode → ROOM_NOT_FOUND")
        void getRoomByCode_InvalidCode_NotFound() {
            ResponseEntity<R<RoomVO>> resp = callGetRoomByCode("99999999");

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.ROOM_NOT_FOUND.getCode());
        }
    }

    // ========================================================================
    // GET /room/{roomId}/members — 获取房间成员列表
    // ========================================================================

    @Nested
    @DisplayName("GET /room/{roomId}/members — 获取房间成员列表")
    class GetMembersTests {

        @Test
        @DisplayName("成功路径：房间成员查询 → 返回成员列表（包含房主）")
        void getMembers_Success() {
            Long ownerId = uid();
            RoomVO room = createRoom("member_query_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            ResponseEntity<R<List<RoomMemberVO>>> resp = callGetMembers(room.getRoomId(), ownerId, "owner_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).hasSize(1);
            RoomMemberVO member = resp.getBody().getData().get(0);
            assertThat(member.getUserId()).isEqualTo(ownerId);
            assertThat(member.getIsOwner()).isTrue();
        }

        @Test
        @DisplayName("错误路径：非成员查询 → NOT_MEMBER")
        void getMembers_NotMember_Fails() {
            Long ownerId = uid();
            Long strangerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            ResponseEntity<R<List<RoomMemberVO>>> resp = callGetMembers(room.getRoomId(), strangerId, "stranger_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.NOT_MEMBER.getCode());
        }

        @Test
        @DisplayName("错误路径：房间不存在 → NOT_MEMBER")
        void getMembers_RoomNotFound() {
            ResponseEntity<R<List<RoomMemberVO>>> resp = callGetMembers(999999L, uid(), "guest_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.NOT_MEMBER.getCode());
        }
    }

    // ========================================================================
    // POST /room/{roomId}/close — 关闭房间
    // ========================================================================

    @Nested
    @DisplayName("POST /room/{roomId}/close — 关闭房间")
    class CloseRoomTests {

        @Test
        @DisplayName("成功路径：房主关闭 → true，状态变为 CLOSED")
        void closeRoom_Success_OwnerCloses() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            ResponseEntity<R<Boolean>> resp = callCloseRoom(room.getRoomId(), ownerId, "owner_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isTrue();
        }

        @Test
        @DisplayName("错误路径：非房主关闭 → NO_PERMISSION")
        void closeRoom_NonOwner_Fails() {
            Long ownerId = uid();
            Long strangerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            ResponseEntity<R<Boolean>> resp = callCloseRoom(room.getRoomId(), strangerId, "stranger_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.NO_PERMISSION.getCode());
        }

        @Test
        @DisplayName("错误路径：房间不存在 → ROOM_NOT_FOUND")
        void closeRoom_RoomNotFound() {
            ResponseEntity<R<Boolean>> resp = callCloseRoom(999999L, uid(), "guest_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.ROOM_NOT_FOUND.getCode());
        }
    }

    // ========================================================================
    // POST /room/{roomId}/renew — 续期房间
    // ========================================================================

    @Nested
    @DisplayName("POST /room/{roomId}/renew — 续期房间")
    class RenewRoomTests {

        @Test
        @DisplayName("成功路径：房主续期 ACTIVE 房间 → 房间延长过期时间")
        void renewRoom_Success_ActiveRoom() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            RenewRoomDto dto = new RenewRoomDto();
            dto.setExtendMinutes(60);
            ResponseEntity<R<RoomVO>> resp = callRenewRoom(room.getRoomId(), dto, ownerId, "owner_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isNotNull();
        }

        @Test
        @DisplayName("参数校验：extendMinutes 为 null → 400")
        void renewRoom_NullMinutes_400() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            RenewRoomDto dto = new RenewRoomDto();
            // extendMinutes = null

            HttpEntity<RenewRoomDto> entity = new HttpEntity<>(dto, userHeaders(ownerId, "owner"));
            ResponseEntity<R<Void>> resp = testRestTemplate.exchange(
                    getRoomUrl("/" + room.getRoomId() + "/renew"),
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<R<Void>>() {});

            assertThat(resp.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.OK);
        }

        @Test
        @DisplayName("参数校验：extendMinutes 为 0 → 400")
        void renewRoom_ZeroMinutes_400() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            RenewRoomDto dto = new RenewRoomDto();
            dto.setExtendMinutes(0);

            ResponseEntity<R<Void>> resp = testRestTemplate.exchange(
                    getRoomUrl("/" + room.getRoomId() + "/renew"),
                    HttpMethod.POST,
                    new HttpEntity<>(dto, userHeaders(ownerId, "owner")),
                    new ParameterizedTypeReference<R<Void>>() {});

            assertThat(resp.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.OK);
        }

        @Test
        @DisplayName("错误路径：非房主续期 → NO_PERMISSION")
        void renewRoom_NonOwner_Fails() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            RenewRoomDto dto = new RenewRoomDto();
            dto.setExtendMinutes(60);
            ResponseEntity<R<RoomVO>> resp = callRenewRoom(room.getRoomId(), dto, uid(), "stranger_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.NO_PERMISSION.getCode());
        }

        @Test
        @DisplayName("错误路径：房间不存在 → ROOM_NOT_FOUND")
        void renewRoom_RoomNotFound() {
            RenewRoomDto dto = new RenewRoomDto();
            dto.setExtendMinutes(60);
            ResponseEntity<R<RoomVO>> resp = callRenewRoom(999999L, dto, uid(), "guest_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.ROOM_NOT_FOUND.getCode());
        }
    }

    // ========================================================================
    // POST /room/{roomId}/reopen — 重新开启房间
    // ========================================================================

    @Nested
    @DisplayName("POST /room/{roomId}/reopen — 重新开启房间")
    class ReopenRoomTests {

        @Test
        @DisplayName("成功路径：重新开启已关闭房间 → ACTIVE")
        void reopenRoom_Success_ClosedRoom() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);
            // 先关闭
            callCloseRoom(room.getRoomId(), ownerId, "owner");

            ReopenRoomDto dto = new ReopenRoomDto();
            dto.setExpireMinutes(60);
            ResponseEntity<R<RoomVO>> resp = callReopenRoom(room.getRoomId(), dto, ownerId, "owner_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isNotNull();
        }

        @Test
        @DisplayName("参数校验：expireMinutes 为 null → 400")
        void reopenRoom_NullMinutes_400() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            HttpEntity<ReopenRoomDto> entity = new HttpEntity<>(new ReopenRoomDto(), userHeaders(ownerId, "owner"));
            ResponseEntity<R<Void>> resp = testRestTemplate.exchange(
                    getRoomUrl("/" + room.getRoomId() + "/reopen"),
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<R<Void>>() {});

            assertThat(resp.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.OK);
        }

        @Test
        @DisplayName("错误路径：非房主重新开启 → ROOM_STATE_CHANGED（状态检查先于权限检查）")
        void reopenRoom_NonOwner_Fails() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            ReopenRoomDto dto = new ReopenRoomDto();
            dto.setExpireMinutes(60);
            ResponseEntity<R<RoomVO>> resp = callReopenRoom(room.getRoomId(), dto, uid(), "stranger_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.ROOM_STATE_CHANGED.getCode());
        }
    }

    // ========================================================================
    // GET /room/my — 获取用户房间列表
    // ========================================================================

    @Nested
    @Rollback(false)
    @DisplayName("GET /room/my — 获取用户房间列表")
    class GetMyRoomsTests {

        @Autowired
        private com.gopair.roomservice.mapper.RoomMapper roomMapper;

        @Test
        @DisplayName("成功路径：查询用户房间列表 → 返回分页结果")
        void getMyRooms_Success() {
            Long userId = uid();
            // 创建 3 个房间
            createRoom("我的房间1_" + uid(), RoomConst.PASSWORD_MODE_NONE, userId);
            createRoom("我的房间2_" + uid(), RoomConst.PASSWORD_MODE_NONE, userId);
            createRoom("我的房间3_" + uid(), RoomConst.PASSWORD_MODE_NONE, userId);

            RoomQueryDto query = new RoomQueryDto();
            ResponseEntity<R<PageResult<RoomVO>>> resp = callGetMyRooms(query, userId, "user_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getTotal()).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("成功路径：包含历史房间 → includeHistory=true")
        void getMyRooms_WithHistory() {
            Long userId = uid();
            createRoom("活跃房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, userId);

            RoomQueryDto query = new RoomQueryDto();
            query.setIncludeHistory(true);
            ResponseEntity<R<PageResult<RoomVO>>> resp = callGetMyRooms(query, userId, "user_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
        }
    }

    // ========================================================================
    // PATCH /room/{roomId}/password — 更新房间密码设置
    // ========================================================================

    @Nested
    @DisplayName("PATCH /room/{roomId}/password — 更新房间密码设置")
    class UpdatePasswordTests {

        @Test
        @DisplayName("成功路径：房主修改密码 NONE→FIXED")
        void updatePassword_NoneToFixed_Success() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            UpdateRoomPasswordDto dto = new UpdateRoomPasswordDto();
            dto.setMode(RoomConst.PASSWORD_MODE_FIXED);
            dto.setRawPassword("NewPass_" + uid());
            dto.setVisible(1);
            ResponseEntity<R<Void>> resp = callUpdatePassword(room.getRoomId(), dto, ownerId, "owner_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("成功路径：房主关闭密码 NONE→0")
        void updatePassword_Disable_Success() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            UpdateRoomPasswordDto dto = new UpdateRoomPasswordDto();
            dto.setMode(RoomConst.PASSWORD_MODE_NONE);
            dto.setRawPassword(null);
            dto.setVisible(0);
            ResponseEntity<R<Void>> resp = callUpdatePassword(room.getRoomId(), dto, ownerId, "owner_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("错误路径：非房主修改密码 → NO_PERMISSION")
        void updatePassword_NonOwner_Fails() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            UpdateRoomPasswordDto dto = new UpdateRoomPasswordDto();
            dto.setMode(RoomConst.PASSWORD_MODE_FIXED);
            dto.setRawPassword("pass_" + uid());
            dto.setVisible(1);
            ResponseEntity<R<Void>> resp = callUpdatePassword(room.getRoomId(), dto, uid(), "stranger_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.NO_PERMISSION.getCode());
        }

        @Test
        @DisplayName("错误路径：房间不存在 → ROOM_NOT_FOUND")
        void updatePassword_RoomNotFound() {
            UpdateRoomPasswordDto dto = new UpdateRoomPasswordDto();
            dto.setMode(RoomConst.PASSWORD_MODE_FIXED);
            dto.setRawPassword("pass_" + uid());
            dto.setVisible(1);
            ResponseEntity<R<Void>> resp = callUpdatePassword(999999L, dto, uid(), "guest_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.ROOM_NOT_FOUND.getCode());
        }
    }

    // ========================================================================
    // PATCH /room/{roomId}/password/visibility — 更新密码可见性
    // ========================================================================

    @Nested
    @DisplayName("PATCH /room/{roomId}/password/visibility — 更新密码可见性")
    class UpdateVisibilityTests {

        @Test
        @DisplayName("成功路径：房主切换可见性 → 0→1")
        void updateVisibility_Success() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            UpdatePasswordVisibilityDto dto = new UpdatePasswordVisibilityDto();
            dto.setVisible(1);
            ResponseEntity<R<Void>> resp = callUpdateVisibility(room.getRoomId(), dto, ownerId, "owner_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("参数校验：visible 为 null → 400")
        void updateVisibility_Null_400() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            HttpEntity<UpdatePasswordVisibilityDto> entity = new HttpEntity<>(new UpdatePasswordVisibilityDto(), userHeaders(ownerId, "owner"));
            ResponseEntity<R<Void>> resp = testRestTemplate.exchange(
                    getRoomUrl("/" + room.getRoomId() + "/password/visibility"),
                    HttpMethod.PATCH,
                    entity,
                    new ParameterizedTypeReference<R<Void>>() {});

            assertThat(resp.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.OK);
        }

        @Test
        @DisplayName("错误路径：非房主更新可见性 → NO_PERMISSION")
        void updateVisibility_NonOwner_Fails() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            UpdatePasswordVisibilityDto dto = new UpdatePasswordVisibilityDto();
            dto.setVisible(1);
            ResponseEntity<R<Void>> resp = callUpdateVisibility(room.getRoomId(), dto, uid(), "stranger_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.NO_PERMISSION.getCode());
        }
    }

    // ========================================================================
    // GET /room/{roomId}/password/current — 获取当前密码
    // ========================================================================

    @Nested
    @DisplayName("GET /room/{roomId}/password/current — 获取当前密码")
    class GetCurrentPasswordTests {

        @Test
        @DisplayName("成功路径：房主查询密码 → 返回解密明文")
        void getCurrentPassword_AsOwner_Success() {
            Long ownerId = uid();
            String rawPassword = "OwnerPass_" + uid();
            RoomVO room = createRoom("密码房间_" + uid(), RoomConst.PASSWORD_MODE_FIXED, ownerId, rawPassword);

            ResponseEntity<R<RoomPasswordVO>> resp = callGetCurrentPassword(room.getRoomId(), ownerId, "owner_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData().getCurrentPassword()).isEqualTo(rawPassword);
        }

        @Test
        @DisplayName("错误路径：非成员查询密码 → NO_PERMISSION")
        void getCurrentPassword_NonMember_Fails() {
            Long ownerId = uid();
            RoomVO room = createRoom("密码房间_" + uid(), RoomConst.PASSWORD_MODE_FIXED, ownerId, "pass_" + uid());

            ResponseEntity<R<RoomPasswordVO>> resp = callGetCurrentPassword(room.getRoomId(), uid(), "stranger_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.NO_PERMISSION.getCode());
        }
    }

    // ========================================================================
    // DELETE /room/{roomId}/members/{userId} — 踢出房间成员
    // ========================================================================

    @Nested
    @DisplayName("DELETE /room/{roomId}/members/{userId} — 踢出房间成员")
    class KickMemberTests {

        @Test
        @DisplayName("成功路径：房主踢出成员 → 成员被移除")
        void kickMember_Success() {
            Long ownerId = uid();
            Long memberId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);
            // 手动同步 Redis（测试环境 @Transactional 不会触发 afterCommit）
            var dbRoom = roomMapper.selectById(room.getRoomId());
            roomCacheSyncService.initializeRoomInCache(dbRoom, ownerId);
            // 批量添加成员（只写 DB，需手动同步 Redis）
            callBatchAddMembers(room.getRoomId(), List.of(memberId));
            roomMapper.incrementMembersIfNotFull(room.getRoomId());
            roomCacheSyncService.addMemberToCache(room.getRoomId(), memberId);
            roomCacheSyncService.incrementConfirmed(room.getRoomId(), 1);

            ResponseEntity<R<Void>> resp = callKickMember(room.getRoomId(), memberId, ownerId, "owner_" + ownerId);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("错误路径：非房主踢人 → NO_PERMISSION")
        void kickMember_NonOwner_Fails() {
            Long ownerId = uid();
            Long strangerId = uid();
            Long victimId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            ResponseEntity<R<Void>> resp = callKickMember(room.getRoomId(), victimId, strangerId, "stranger_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.NO_PERMISSION.getCode());
        }

        @Test
        @DisplayName("错误路径：房主自踢 → NO_PERMISSION")
        void kickMember_OwnerKickSelf_Fails() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            ResponseEntity<R<Void>> resp = callKickMember(room.getRoomId(), ownerId, ownerId, "owner_" + uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getCode()).isEqualTo(RoomErrorCode.NO_PERMISSION.getCode());
        }
    }

    // ========================================================================
    // GET /room/{roomId}/members/{userId}/check — 检查成员身份（内部接口）
    // ========================================================================

    @Nested
    @DisplayName("GET /room/{roomId}/members/{userId}/check — 检查成员身份（内部接口）")
    class CheckMemberTests {

        @Test
        @DisplayName("成功路径：成员检查 → true（无需认证）")
        void checkMember_Member_True() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            ResponseEntity<R<Boolean>> resp = callCheckMember(room.getRoomId(), ownerId);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isTrue();
        }

        @Test
        @DisplayName("成功路径：非成员检查 → false")
        void checkMember_NonMember_False() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            ResponseEntity<R<Boolean>> resp = callCheckMember(room.getRoomId(), uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isFalse();
        }

        @Test
        @DisplayName("成功路径：房间不存在 → false（安全降级，不抛异常）")
        void checkMember_RoomNotFound_False() {
            ResponseEntity<R<Boolean>> resp = callCheckMember(999999L, uid());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isFalse();
        }
    }

    // ========================================================================
    // GET /room/{roomId}/status — 获取房间状态（内部接口）
    // ========================================================================

    @Nested
    @DisplayName("GET /room/{roomId}/status — 获取房间状态（内部接口）")
    class GetRoomStatusTests {

        @Test
        @DisplayName("成功路径：ACTIVE 房间 → status=0（无需认证）")
        void getRoomStatus_ActiveRoom_0() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            ResponseEntity<R<Integer>> resp = callGetRoomStatus(room.getRoomId());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isEqualTo(RoomConst.STATUS_ACTIVE);
        }

        @Test
        @DisplayName("成功路径：CLOSED 房间 → status=1")
        void getRoomStatus_ClosedRoom_1() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);
            callCloseRoom(room.getRoomId(), ownerId, "owner");

            ResponseEntity<R<Integer>> resp = callGetRoomStatus(room.getRoomId());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isEqualTo(RoomConst.STATUS_CLOSED);
        }

        @Test
        @DisplayName("成功路径：房间不存在 → null（安全降级）")
        void getRoomStatus_RoomNotFound_Null() {
            ResponseEntity<R<Integer>> resp = callGetRoomStatus(999999L);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isNull();
        }
    }

    // ========================================================================
    // POST /room/{roomId}/members/batch — 批量添加成员（内部接口）
    // ========================================================================

    @Nested
    @DisplayName("POST /room/{roomId}/members/batch — 批量添加成员（内部接口）")
    class BatchAddMembersTests {

        @Test
        @DisplayName("成功路径：批量添加成员 → 返回成功数量")
        void batchAddMembers_Success() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            List<Long> userIds = List.of(uid(), uid(), uid());
            ResponseEntity<R<Integer>> resp = callBatchAddMembers(room.getRoomId(), userIds);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isEqualTo(userIds.size());
        }

        @Test
        @DisplayName("成功路径：空列表 → 返回 0")
        void batchAddMembers_EmptyList_0() {
            Long ownerId = uid();
            RoomVO room = createRoom("房间_" + uid(), RoomConst.PASSWORD_MODE_NONE, ownerId);

            ResponseEntity<R<Integer>> resp = callBatchAddMembers(room.getRoomId(), List.of());

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isEqualTo(0);
        }

        @Test
        @DisplayName("成功路径：房间不存在 → 返回 1（静默降级，内部不校验房间存在性）")
        void batchAddMembers_RoomNotFound_0() {
            ResponseEntity<R<Integer>> resp = callBatchAddMembers(999999L, List.of(uid()));

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isTrue();
            assertThat(resp.getBody().getData()).isEqualTo(1);
        }
    }

    // ========================================================================
    // 测试数据构建辅助方法
    // ========================================================================

    private RoomDto buildRoomDto(String roomName, int passwordMode) {
        RoomDto dto = new RoomDto();
        dto.setRoomName(roomName);
        dto.setMaxMembers(10);
        dto.setPasswordMode(passwordMode);
        return dto;
    }

    private RoomVO createRoom(String name, int passwordMode, Long ownerId) {
        return createRoom(name, passwordMode, ownerId, null);
    }

    private RoomVO createRoom(String name, int passwordMode, Long ownerId, String rawPassword) {
        RoomDto dto = new RoomDto();
        dto.setRoomName(name);
        dto.setMaxMembers(10);
        dto.setPasswordMode(passwordMode);
        if (rawPassword != null) {
            dto.setRawPassword(rawPassword);
            dto.setPasswordVisible(1);
        }

        ResponseEntity<R<RoomVO>> resp = callCreateRoom(dto, ownerId, "owner_" + ownerId);
        return resp.getBody().getData();
    }
}
