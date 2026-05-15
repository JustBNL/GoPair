package com.gopair.voiceservice.controller;

import com.gopair.common.constants.SystemConstants;
import com.gopair.common.core.R;
import com.gopair.framework.config.FrameworkAutoConfiguration;
import com.gopair.voiceservice.base.BaseIntegrationTest;
import com.gopair.voiceservice.base.RoomStatusStubber;
import com.gopair.voiceservice.base.TestDataCleaner;
import com.gopair.voiceservice.domain.dto.SignalingDto;
import com.gopair.voiceservice.domain.vo.CallVO;
import com.gopair.voiceservice.enums.CallStatus;
import com.gopair.voiceservice.mapper.VoiceCallMapper;
import com.gopair.voiceservice.mapper.VoiceCallParticipantMapper;
import com.gopair.voiceservice.service.VoiceCallService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * 语音通话 Controller 层集成测试。
 *
 * * [核心策略]
 * - 真实 HTTP 请求 + X-User-Id / X-Nickname 请求头模拟用户上下文。
 * - @MockBean WebSocketMessageProducer / RabbitMQ：验证推送调用次数但不建立真实连接。
 * - TestDataCleaner：每个测试方法结束后手动清理测试数据（不使用 @Transactional）。
 * - 跨线程事务可见性：TestRestTemplate 发起 HTTP 请求，线程与测试线程不同，
 *   必须确保测试数据已提交，Controller 才能读到。
 *
 * * [测试编排]
 * - 主干流 A：joinOrCreateCall -> notifyReady -> leaveCall -> endCall（完整通话链路）
 * - 分支流 B：getCall -> getActiveCall -> ownerLeave -> joinCall（查询 + 房主退出）
 * - 边界流 C：getCall 不存在 / joinCall 已结束通话 / forwardSignaling 非参与者
 *
 * @author gopair
 */
@Slf4j
@DisplayName("语音通话 Controller 层集成测试")
@Import({FrameworkAutoConfiguration.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VoiceControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VoiceCallService voiceCallService;

    @Autowired
    private VoiceCallMapper voiceCallMapper;

    @Autowired
    private VoiceCallParticipantMapper participantMapper;

    @Autowired
    private TestDataCleaner testDataCleaner;

    private static final Long ROOM_ID_BASE = 50000L;
    private static long roomIdCounter = 0;

    private static synchronized Long nextRoomId() {
        return ROOM_ID_BASE + (++roomIdCounter);
    }

    private Long freshRoomId() {
        return ROOM_ID_BASE + 20000L + (System.nanoTime() % 50000L);
    }

    @BeforeEach
    void baseSetup() {
        testDataCleaner.cleanupAll();
        var factory = stringRedisTemplate.getConnectionFactory();
        if (factory != null && factory.getConnection() != null) {
            factory.getConnection().serverCommands().flushDb();
        }
    }

    // ==================== 主干流 A：完整通话生命周期 HTTP 链路 ====================

    @Nested
    @DisplayName("主干流 A：完整通话生命周期 HTTP 链路")
    class FullCallLifecycleFlow {

        @Test
        @Order(1)
        @DisplayName("Step 1: POST /voice/room/{roomId}/join -> 创建或加入通话")
        void joinOrCreateCall_ShouldReturnCallVO() {
            Long roomId = nextRoomId();
            Long userId = 60001L;

            log.info("==== [Step 1: POST /voice/room/{}/join] ====", roomId);

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/room/" + roomId + "/join"),
                    new HttpEntity<>(userHeaders(userId, "UserA")),
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(200);

            CallVO call = objectMapper.convertValue(response.getBody().getData(), CallVO.class);
            assertThat(call.getCallId()).isNotNull();
            assertThat(call.getRoomId()).isEqualTo(roomId);
            assertThat(call.getInitiatorId()).isEqualTo(userId);
            assertThat(call.getStatus()).isEqualTo(CallStatus.IN_PROGRESS.getCode());
            assertThat(call.isJoinable()).isTrue();

            log.info("通话创建成功: callId={}, roomId={}, initiatorId={}",
                    call.getCallId(), roomId, userId);
        }

        @Test
        @Order(2)
        @DisplayName("Step 2: POST /voice/{callId}/ready -> WebRTC 就绪通知")
        void notifyReady_ShouldReturn200() {
            Long roomId = nextRoomId();
            Long userId = 60010L;

            CallVO call = voiceCallService.joinOrCreateCall(roomId, userId);

            log.info("==== [Step 2: POST /voice/{}/ready] ====", call.getCallId());

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/" + call.getCallId() + "/ready"),
                    new HttpEntity<>(userHeaders(userId, "UserB")),
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(200);

            log.info("WebRTC 就绪通知成功: callId={}, userId={}", call.getCallId(), userId);
        }

        @Test
        @Order(3)
        @DisplayName("Step 3: POST /voice/{callId}/leave -> 离开通话")
        void leaveCall_ShouldReturn200() {
            Long roomId = nextRoomId();
            Long ownerId = 60020L;
            Long userId = 60021L;

            CallVO call = voiceCallService.joinOrCreateCall(roomId, ownerId);
            voiceCallService.joinCall(call.getCallId(), userId);

            log.info("==== [Step 3: POST /voice/{}/leave] ====", call.getCallId());

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/" + call.getCallId() + "/leave"),
                    new HttpEntity<>(userHeaders(userId, "UserC")),
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(200);

            log.info("离开通话成功: callId={}, userId={}", call.getCallId(), userId);
        }

        @Test
        @Order(4)
        @DisplayName("Step 4: POST /voice/{callId}/end -> 结束通话")
        void endCall_ShouldReturn200() {
            Long roomId = nextRoomId();
            Long ownerId = 60030L;

            CallVO call = voiceCallService.joinOrCreateCall(roomId, ownerId);

            log.info("==== [Step 4: POST /voice/{}/end] ====", call.getCallId());

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/" + call.getCallId() + "/end"),
                    new HttpEntity<>(userHeaders(ownerId, "OwnerD")),
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(200);

            log.info("结束通话成功: callId={}, ownerId={}", call.getCallId(), ownerId);
        }
    }

    // ==================== 分支流 B：查询 + 主动结束 ====================

    @Nested
    @DisplayName("分支流 B：查询 + 主动结束")
    class QueryAndEndFlow {

        @Test
        @Order(1)
        @DisplayName("Step 1: GET /voice/{callId} -> 查询通话信息")
        void getCall_ShouldReturnCallVO() {
            Long roomId = nextRoomId();
            Long userId = 60040L;

            CallVO call = voiceCallService.joinOrCreateCall(roomId, userId);

            log.info("==== [Step 1: GET /voice/{}] ====", call.getCallId());

            ResponseEntity<R> response = testRestTemplate.getForEntity(
                    getUrl("/voice/" + call.getCallId()),
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(200);

            CallVO result = objectMapper.convertValue(response.getBody().getData(), CallVO.class);
            assertThat(result.getCallId()).isEqualTo(call.getCallId());
            assertThat(result.getRoomId()).isEqualTo(roomId);
            assertThat(result.getInitiatorId()).isEqualTo(userId);
            assertThat(result.isJoinable()).isTrue();

            log.info("查询通话成功: callId={}, status={}", result.getCallId(), result.getStatus());
        }

        @Test
        @Order(2)
        @DisplayName("Step 2: GET /voice/room/{roomId}/active -> 获取房间活跃通话")
        void getActiveCall_ShouldReturnCallOrNull() {
            Long roomId = nextRoomId();
            Long userId = 60050L;

            CallVO call = voiceCallService.joinOrCreateCall(roomId, userId);

            log.info("==== [Step 2: GET /voice/room/{}/active] ====", roomId);

            ResponseEntity<R> response = testRestTemplate.getForEntity(
                    getUrl("/voice/room/" + roomId + "/active"),
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(200);

            CallVO result = objectMapper.convertValue(response.getBody().getData(), CallVO.class);
            assertThat(result).isNotNull();
            assertThat(result.getCallId()).isEqualTo(call.getCallId());

            log.info("获取活跃通话成功: callId={}, roomId={}", result.getCallId(), roomId);
        }

        @Test
        @Order(3)
        @DisplayName("Step 3: POST /voice/{callId}/owner-leave -> 房主退出（通话继续）")
        void ownerLeave_ShouldReturn200() {
            Long roomId = nextRoomId();
            Long ownerId = 60060L;
            Long userId = 60061L;

            CallVO call = voiceCallService.joinOrCreateCall(roomId, ownerId);
            voiceCallService.joinCall(call.getCallId(), userId);

            log.info("==== [Step 3: POST /voice/{}/owner-leave] ====", call.getCallId());

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/" + call.getCallId() + "/owner-leave"),
                    new HttpEntity<>(userHeaders(ownerId, "OwnerE")),
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(200);

            log.info("房主退出成功: callId={}, ownerId={}", call.getCallId(), ownerId);
        }

        @Test
        @Order(4)
        @DisplayName("Step 4: POST /voice/{callId}/join -> 通过 callId 加入通话")
        void joinCallById_ShouldReturnCallVO() {
            Long roomId = nextRoomId();
            Long ownerId = 60070L;
            Long userId = 60071L;

            CallVO call = voiceCallService.joinOrCreateCall(roomId, ownerId);

            log.info("==== [Step 4: POST /voice/{}/join] ====", call.getCallId());

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/" + call.getCallId() + "/join"),
                    new HttpEntity<>(userHeaders(userId, "JoinerF")),
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(200);

            CallVO result = objectMapper.convertValue(response.getBody().getData(), CallVO.class);
            assertThat(result.getCallId()).isEqualTo(call.getCallId());
            assertThat(result.getParticipantCount()).isEqualTo(2);

            log.info("通过 callId 加入通话成功: callId={}, userId={}, participantCount={}",
                    call.getCallId(), userId, result.getParticipantCount());
        }
    }

    // ==================== 边界流 C：异常路径 ====================

    @Nested
    @DisplayName("边界流 C：异常路径")
    class EdgeCaseFlow {

        private void flushRedis() {
            var factory = stringRedisTemplate.getConnectionFactory();
            if (factory != null && factory.getConnection() != null) {
                factory.getConnection().serverCommands().flushDb();
            }
        }

        @BeforeEach
        void cleanup() {
            testDataCleaner.cleanupAll();
            flushRedis();
        }

        @Test
        @Order(1)
        @DisplayName("Step 1: GET /voice/{不存在callId} -> 返回错误")
        void getCall_NotFound_ShouldReturnError() {
            Long nonExistCallId = 900000L + (System.nanoTime() % 99999L);

            log.info("==== [Step 1: GET /voice/{} 不存在] ====", nonExistCallId);

            ResponseEntity<R> response = testRestTemplate.getForEntity(
                    getUrl("/voice/" + nonExistCallId), R.class);

            assertThat(response.getStatusCode().value()).isNotEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isNotEqualTo(200);

            log.info("查询不存在通话: status={}, code={}",
                    response.getStatusCode().value(), response.getBody().getCode());
        }

        @Test
        @Order(2)
        @DisplayName("Step 2: GET /voice/room/{roomId}/active -> 无活跃通话时返回 null")
        void getActiveCall_NoActiveCall_ShouldReturnNull() {
            Long emptyRoomId = freshRoomId();

            log.info("==== [Step 2: GET /voice/room/{}/active 无活跃通话] ====", emptyRoomId);

            ResponseEntity<R> response = testRestTemplate.getForEntity(
                    getUrl("/voice/room/" + emptyRoomId + "/active"),
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(200);

            Object data = response.getBody().getData();
            assertThat(data).isNull();

            log.info("无活跃通话: roomId={}, data=null", emptyRoomId);
        }

        @Test
        @Order(3)
        @DisplayName("Step 3: POST /voice/{callId}/join -> 通话已结束时抛异常")
        void joinCall_EndedCall_ShouldReturnError() {
            Long roomId = nextRoomId();
            Long ownerId = 60080L;
            Long userId = 60081L;

            CallVO call = voiceCallService.joinOrCreateCall(roomId, ownerId);
            voiceCallService.leaveCall(call.getCallId(), ownerId);

            log.info("==== [Step 3: POST /voice/{}/join 通话已结束] ====", call.getCallId());

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/" + call.getCallId() + "/join"),
                    new HttpEntity<>(userHeaders(userId, "LateJoiner")),
                    R.class);

            assertThat(response.getStatusCode().value()).isNotEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isNotEqualTo(200);

            log.info("加入已结束通话被拒绝: status={}, code={}",
                    response.getStatusCode().value(), response.getBody().getCode());
        }

        @Test
        @Order(4)
        @DisplayName("Step 4: POST /voice/signaling -> 非参与者信令转发被静默丢弃")
        void forwardSignaling_NonParticipant_ShouldReturn200() {
            Long roomId = nextRoomId();
            Long ownerId = 60090L;
            Long strangerId = 60091L;

            CallVO call = voiceCallService.joinOrCreateCall(roomId, ownerId);

            SignalingDto dto = new SignalingDto();
            dto.setCallId(call.getCallId());
            dto.setType("offer");
            dto.setTargetUserId(ownerId);

            log.info("==== [Step 4: POST /voice/signaling 非参与者转发] ====");

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/signaling"),
                    new HttpEntity<>(dto, userHeaders(strangerId, "Stranger")),
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(200);

            log.info("非参与者信令转发: code={}（静默丢弃，不抛异常）", response.getBody().getCode());
        }
    }

    // ==================== 分支流 D：房间关闭优雅终止 ====================

    @Nested
    @DisplayName("分支流 D：房间关闭优雅终止")
    class RoomCloseGracefulTerminateFlow {

        private void flushRedis() {
            var factory = stringRedisTemplate.getConnectionFactory();
            if (factory != null && factory.getConnection() != null) {
                factory.getConnection().serverCommands().flushDb();
            }
        }

        @BeforeEach
        void cleanup() {
            testDataCleaner.cleanupAll();
            flushRedis();
        }

        @Test
        @Order(1)
        @DisplayName("POST /voice/room/{roomId}/end-all -> 有活跃通话时终止所有通话")
        void endAllCalls_WithActiveCalls_ShouldTerminateAll() {
            Long roomId = nextRoomId();
            Long ownerId = 60100L;
            Long userId1 = 60101L;
            Long userId2 = 60102L;

            // 场景：创建两个通话（ownerId 和 userId1 各创建一个）
            CallVO call1 = voiceCallService.joinOrCreateCall(roomId, ownerId);
            voiceCallService.joinCall(call1.getCallId(), userId1);

            Long roomId2 = nextRoomId();
            CallVO call2 = voiceCallService.joinOrCreateCall(roomId2, userId2);

            log.info("==== [POST /voice/room/{}/end-all] ====", roomId);

            // 调用 end-all 终止 roomId 下的所有通话
            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/room/" + roomId + "/end-all"),
                    null,
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(200);
            assertThat(response.getBody().getData()).isEqualTo(1);

            // 验证通话1状态变为 ENDED
            Object[] call1Row = selectCall(call1.getCallId());
            assertThat(call1Row).isNotNull();
            assertThat(call1Row[4]).isEqualTo(CallStatus.ENDED.getCode()); // status
            assertThat(call1Row[6]).isNotNull(); // end_time

            // 验证通话2（不同房间）状态不变
            Object[] call2Row = selectCall(call2.getCallId());
            assertThat(call2Row).isNotNull();
            assertThat(call2Row[4]).isEqualTo(CallStatus.IN_PROGRESS.getCode()); // status

            log.info("房间{}共终止1个通话(callId={})，另一房间{}通话(callId={})状态保持IN_PROGRESS",
                    roomId, call1.getCallId(), roomId2, call2.getCallId());
        }

        @Test
        @Order(2)
        @DisplayName("POST /voice/room/{roomId}/end-all -> 无活跃通话时返回0")
        void endAllCalls_NoActiveCalls_ShouldReturnZero() {
            Long emptyRoomId = freshRoomId();

            log.info("==== [POST /voice/room/{}/end-all 无活跃通话] ====", emptyRoomId);

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/room/" + emptyRoomId + "/end-all"),
                    null,
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(200);
            assertThat(response.getBody().getData()).isEqualTo(0);

            log.info("无活跃通话: roomId={}, 返回0", emptyRoomId);
        }
    }

    // ==================== 辅助方法 ====================

    private HttpHeaders userHeaders(Long userId, String nickname) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(SystemConstants.HEADER_USER_ID, String.valueOf(userId));
        headers.set(SystemConstants.HEADER_NICKNAME, nickname);
        return headers;
    }

    // ==================== 分支流 E：cleanup 接口 ====================

    @Nested
    @DisplayName("分支流 E：cleanup 接口")
    class CleanupFlow {

        @BeforeEach
        void cleanup() {
            testDataCleaner.cleanupAll();
            flushRedis();
        }

        private void flushRedis() {
            var factory = stringRedisTemplate.getConnectionFactory();
            if (factory != null && factory.getConnection() != null) {
                factory.getConnection().serverCommands().flushDb();
            }
        }

        @Test
        @Order(1)
        @DisplayName("POST /voice/room/{roomId}/cleanup -> 有通话记录时清理并返回数量")
        void cleanup_WithExistingCalls_ShouldReturnCount() {
            Long roomId = nextRoomId();
            Long userId = 60200L;

            CallVO call = voiceCallService.joinOrCreateCall(roomId, userId);
            Long callId = call.getCallId();

            log.info("==== [POST /voice/room/{}/cleanup] ====", roomId);

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/room/" + roomId + "/cleanup"),
                    null,
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(200);
            assertThat(response.getBody().getData()).isEqualTo(1);

            // 验证通话已被物理删除
            assertThat(voiceCallMapper.selectById(callId)).isNull();

            log.info("清理成功: roomId={}, 返回清理数量=1, callId={} 已不存在", roomId, callId);
        }

        @Test
        @Order(2)
        @DisplayName("POST /voice/room/{roomId}/cleanup -> 无通话记录时返回 0")
        void cleanup_NoCalls_ShouldReturnZero() {
            Long emptyRoomId = freshRoomId();

            log.info("==== [POST /voice/room/{}/cleanup 无通话] ====", emptyRoomId);

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/room/" + emptyRoomId + "/cleanup"),
                    null,
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(200);
            assertThat(response.getBody().getData()).isEqualTo(0);

            log.info("无通话清理: roomId={}, 返回0", emptyRoomId);
        }
    }

    // ==================== 边界流 F：房间状态校验 ====================

    @Nested
    @DisplayName("边界流 F：房间状态校验")
    class RoomStatusValidationFlow {

        @BeforeEach
        void cleanup() {
            testDataCleaner.cleanupAll();
            flushRedis();
        }

        private void flushRedis() {
            var factory = stringRedisTemplate.getConnectionFactory();
            if (factory != null && factory.getConnection() != null) {
                factory.getConnection().serverCommands().flushDb();
            }
        }

        @Test
        @Order(1)
        @DisplayName("POST /voice/room/{roomId}/join -> 房间 CLOSED 时拒绝")
        void joinOrCreateCall_RoomClosed_ShouldReturnError() {
            Long roomId = nextRoomId();
            Long userId = 60210L;
            RoomStatusStubber.stub(restTemplate, roomId, 1); // CLOSED

            log.info("==== [POST /voice/room/{}/join 房间 CLOSED] ====", roomId);

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/room/" + roomId + "/join"),
                    new HttpEntity<>(userHeaders(userId, "UserClosed")),
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isNotEqualTo(200);

            log.info("房间 CLOSED 被拒绝: roomId={}, code={}", roomId, response.getBody().getCode());
        }

        @Test
        @Order(2)
        @DisplayName("POST /voice/room/{roomId}/join -> 房间 EXPIRED 时拒绝")
        void joinOrCreateCall_RoomExpired_ShouldReturnError() {
            Long roomId = nextRoomId();
            Long userId = 60211L;
            RoomStatusStubber.stub(restTemplate, roomId, 2); // EXPIRED

            log.info("==== [POST /voice/room/{}/join 房间 EXPIRED] ====", roomId);

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/room/" + roomId + "/join"),
                    new HttpEntity<>(userHeaders(userId, "UserExpired")),
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isNotEqualTo(200);

            log.info("房间 EXPIRED 被拒绝: roomId={}, code={}", roomId, response.getBody().getCode());
        }

        @Test
        @Order(3)
        @DisplayName("POST /voice/room/{roomId}/join -> 房间 DISABLED 时拒绝")
        void joinOrCreateCall_RoomDisabled_ShouldReturnError() {
            Long roomId = nextRoomId();
            Long userId = 60212L;
            RoomStatusStubber.stub(restTemplate, roomId, 4); // DISABLED

            log.info("==== [POST /voice/room/{}/join 房间 DISABLED] ====", roomId);

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/room/" + roomId + "/join"),
                    new HttpEntity<>(userHeaders(userId, "UserDisabled")),
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isNotEqualTo(200);

            log.info("房间 DISABLED 被拒绝: roomId={}, code={}", roomId, response.getBody().getCode());
        }

        @Test
        @Order(4)
        @DisplayName("POST /voice/room/{roomId}/join -> 房间 ARCHIVED 时拒绝")
        void joinOrCreateCall_RoomArchived_ShouldReturnError() {
            Long roomId = nextRoomId();
            Long userId = 60213L;
            RoomStatusStubber.stub(restTemplate, roomId, 3); // ARCHIVED

            log.info("==== [POST /voice/room/{}/join 房间 ARCHIVED] ====", roomId);

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/room/" + roomId + "/join"),
                    new HttpEntity<>(userHeaders(userId, "UserArchived")),
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isNotEqualTo(200);

            log.info("房间 ARCHIVED 被拒绝: roomId={}, code={}", roomId, response.getBody().getCode());
        }
    }

    // ==================== 边界流 G：通话不存在时的操作 ====================

    @Nested
    @DisplayName("边界流 G：通话不存在时的操作")
    class CallNotFoundFlow {

        @BeforeEach
        void cleanup() {
            testDataCleaner.cleanupAll();
            flushRedis();
        }

        private void flushRedis() {
            var factory = stringRedisTemplate.getConnectionFactory();
            if (factory != null && factory.getConnection() != null) {
                factory.getConnection().serverCommands().flushDb();
            }
        }

        @Test
        @Order(1)
        @DisplayName("POST /voice/{callId}/ready -> 通话不存在时返回错误")
        void notifyReady_CallNotFound_ShouldReturnError() {
            Long nonExistCallId = 999999L;

            log.info("==== [POST /voice/{}/ready 不存在通话] ====", nonExistCallId);

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/" + nonExistCallId + "/ready"),
                    new HttpEntity<>(userHeaders(60300L, "UserReady")),
                    R.class);

            assertThat(response.getStatusCode().value()).isNotEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isNotEqualTo(200);

            log.info("ready 不存在通话被拒绝: callId={}, code={}",
                    nonExistCallId, response.getBody().getCode());
        }

        @Test
        @Order(2)
        @DisplayName("POST /voice/{callId}/leave -> 通话不存在时返回错误")
        void leaveCall_CallNotFound_ShouldReturnError() {
            Long nonExistCallId = 999998L;

            log.info("==== [POST /voice/{}/leave 不存在通话] ====", nonExistCallId);

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/" + nonExistCallId + "/leave"),
                    new HttpEntity<>(userHeaders(60301L, "UserLeave")),
                    R.class);

            assertThat(response.getStatusCode().value()).isNotEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isNotEqualTo(200);

            log.info("leave 不存在通话被拒绝: callId={}, code={}",
                    nonExistCallId, response.getBody().getCode());
        }

        @Test
        @Order(3)
        @DisplayName("POST /voice/{callId}/end -> 通话不存在时返回错误")
        void endCall_CallNotFound_ShouldReturnError() {
            Long nonExistCallId = 999997L;

            log.info("==== [POST /voice/{}/end 不存在通话] ====", nonExistCallId);

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/" + nonExistCallId + "/end"),
                    new HttpEntity<>(userHeaders(60302L, "UserEnd")),
                    R.class);

            assertThat(response.getStatusCode().value()).isNotEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isNotEqualTo(200);

            log.info("end 不存在通话被拒绝: callId={}, code={}",
                    nonExistCallId, response.getBody().getCode());
        }

        @Test
        @Order(4)
        @DisplayName("POST /voice/{callId}/owner-leave -> 通话不存在时返回错误")
        void ownerLeave_CallNotFound_ShouldReturnError() {
            Long nonExistCallId = 999996L;

            log.info("==== [POST /voice/{}/owner-leave 不存在通话] ====", nonExistCallId);

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/" + nonExistCallId + "/owner-leave"),
                    new HttpEntity<>(userHeaders(60303L, "UserOwnerLeave")),
                    R.class);

            assertThat(response.getStatusCode().value()).isNotEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isNotEqualTo(200);

            log.info("owner-leave 不存在通话被拒绝: callId={}, code={}",
                    nonExistCallId, response.getBody().getCode());
        }
    }

    // ==================== 边界流 H：用户不在通话中的操作 ====================

    @Nested
    @DisplayName("边界流 H：用户不在通话中的操作")
    class UserNotInCallFlow {

        @BeforeEach
        void cleanup() {
            testDataCleaner.cleanupAll();
            flushRedis();
        }

        private void flushRedis() {
            var factory = stringRedisTemplate.getConnectionFactory();
            if (factory != null && factory.getConnection() != null) {
                factory.getConnection().serverCommands().flushDb();
            }
        }

        @Test
        @Order(1)
        @DisplayName("POST /voice/{callId}/leave -> 用户不在通话中时静默返回 200")
        void leaveCall_UserNotInCall_ShouldReturn200() {
            Long roomId = nextRoomId();
            Long ownerId = 60400L;
            Long strangerId = 60401L;

            CallVO call = voiceCallService.joinOrCreateCall(roomId, ownerId);

            log.info("==== [POST /voice/{}/leave 用户不在通话中] ====", call.getCallId());

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/" + call.getCallId() + "/leave"),
                    new HttpEntity<>(userHeaders(strangerId, "Stranger")),
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(200);

            log.info("用户不在通话中离开返回200: callId={}, strangerId={}", call.getCallId(), strangerId);
        }

        @Test
        @Order(2)
        @DisplayName("POST /voice/{callId}/owner-leave -> 用户不在通话中时静默返回 200")
        void ownerLeave_UserNotInCall_ShouldReturn200() {
            Long roomId = nextRoomId();
            Long ownerId = 60410L;
            Long strangerId = 60411L;

            CallVO call = voiceCallService.joinOrCreateCall(roomId, ownerId);

            log.info("==== [POST /voice/{}/owner-leave 用户不在通话中] ====", call.getCallId());

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/" + call.getCallId() + "/owner-leave"),
                    new HttpEntity<>(userHeaders(strangerId, "Stranger")),
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(200);

            log.info("用户不在通话中owner-leave返回200: callId={}, strangerId={}", call.getCallId(), strangerId);
        }
    }

    // ==================== 边界流 I：信令转发边界 ====================

    @Nested
    @DisplayName("边界流 I：信令转发边界")
    class SignalingEdgeFlow {

        @BeforeEach
        void cleanup() {
            testDataCleaner.cleanupAll();
            flushRedis();
        }

        private void flushRedis() {
            var factory = stringRedisTemplate.getConnectionFactory();
            if (factory != null && factory.getConnection() != null) {
                factory.getConnection().serverCommands().flushDb();
            }
        }

        @Test
        @Order(1)
        @DisplayName("POST /voice/signaling -> 通话不存在时静默返回 200（不抛异常）")
        void forwardSignaling_CallNotFound_ShouldReturn200() {
            Long nonExistCallId = 999990L;
            Long userId = 60500L;

            SignalingDto dto = new SignalingDto();
            dto.setCallId(nonExistCallId);
            dto.setType("offer");
            dto.setTargetUserId(60501L);
            dto.setData(Map.of("sdp", "test"));

            log.info("==== [POST /voice/signaling 通话不存在] ====");

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/signaling"),
                    new HttpEntity<>(dto, userHeaders(userId, "UserSig")),
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(200);

            log.info("信令转发到不存在通话静默丢弃: callId={}", nonExistCallId);
        }

        @Test
        @Order(2)
        @DisplayName("POST /voice/signaling -> 参与者成功转发信令")
        void forwardSignaling_Participant_ShouldReturn200() {
            Long roomId = nextRoomId();
            Long ownerId = 60510L;
            Long userId = 60511L;

            CallVO call = voiceCallService.joinOrCreateCall(roomId, ownerId);
            voiceCallService.joinCall(call.getCallId(), userId);

            SignalingDto dto = new SignalingDto();
            dto.setCallId(call.getCallId());
            dto.setType("answer");
            dto.setTargetUserId(ownerId);
            dto.setData(Map.of("sdp", "answer"));

            log.info("==== [POST /voice/signaling 参与者转发] ====");

            ResponseEntity<R> response = testRestTemplate.postForEntity(
                    getUrl("/voice/signaling"),
                    new HttpEntity<>(dto, userHeaders(userId, "UserSig")),
                    R.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(200);

            log.info("参与者信令转发成功: callId={}, from={}, to={}", call.getCallId(), userId, ownerId);
        }
    }
}
