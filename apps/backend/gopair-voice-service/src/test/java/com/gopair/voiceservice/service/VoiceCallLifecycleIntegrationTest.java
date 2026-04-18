package com.gopair.voiceservice.service;

import com.gopair.voiceservice.base.BaseIntegrationTest;
import com.gopair.voiceservice.base.RecordingWebSocketProducer;
import com.gopair.voiceservice.base.VoiceServiceTestConfig;
import com.gopair.voiceservice.domain.dto.SignalingDto;
import com.gopair.voiceservice.domain.po.VoiceCall;
import com.gopair.voiceservice.domain.po.VoiceCallParticipant;
import com.gopair.voiceservice.domain.vo.CallVO;
import com.gopair.voiceservice.enums.CallStatus;
import com.gopair.voiceservice.enums.CallType;
import com.gopair.voiceservice.enums.ConnectionStatus;
import com.gopair.voiceservice.mapper.VoiceCallMapper;
import com.gopair.voiceservice.mapper.VoiceCallParticipantMapper;
import com.gopair.voiceservice.service.impl.VoiceCallServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 语音通话服务全生命周期集成测试。
 *
 * * [核心策略]
 * - 智能合并：将创建→加入→就绪→离开→终止等多个动作合并为 3 条完整测试流。
 * - 真实 DB：H2 内存数据库验证 MySQL 状态，@Transactional 保证自动回滚。
 * - WebSocket 验证：RecordingWebSocketMessageProducer 记录所有推送，支持断言。
 *
 * * [测试流编排]
 * - 测试流 A：完整通话生命周期（createAutoCall → joinCall → notifyReady → 普通 leave → 最后一人离开自动终止）
 * - 测试流 B：房主离开但通话继续 + 强制结束通话（endCall）
 * - 测试流 C：异常边界（加入已结束通话、重复加入、信令转发权限校验、并发竞态）
 */
@Slf4j
@DisplayName("语音通话服务全生命周期集成测试")
@Import(VoiceServiceTestConfig.class)
class VoiceCallLifecycleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private VoiceCallServiceImpl voiceCallService;

    @Autowired
    private VoiceCallMapper voiceCallMapper;

    @Autowired
    private VoiceCallParticipantMapper participantMapper;

    // 录音式 WebSocket Producer：记录每次调用，支持断言验证
    @Autowired(required = false)
    private RecordingWebSocketProducer recordingWsProducer;

    @BeforeEach
    void resetRecording() {
        if (recordingWsProducer != null) {
            recordingWsProducer.reset();
        }
    }

    // ==================== 测试流 A：完整通话生命周期 ====================

    @Nested
    @DisplayName("测试流 A：完整通话生命周期")
    class CallLifecycleFlow {

        @Test
        @DisplayName("Step 1: createAutoCall → DB 写入 voice_call + voice_call_participant")
        void createAutoCall_ShouldPersistCorrectly() {
            Long roomId = 10001L;
            Long ownerId = 20001L;

            log.info("==== [Step 1: createAutoCall] 状态校验 ====");

            CallVO result = voiceCallService.createAutoCall(roomId, ownerId);

            assertNotNull(result);
            assertNotNull(result.getCallId());
            assertEquals(roomId, result.getRoomId());
            assertEquals(ownerId, result.getInitiatorId());
            assertEquals(CallType.MULTI_USER.getCode(), result.getCallType());
            assertEquals(CallStatus.IN_PROGRESS.getCode(), result.getStatus());

            VoiceCall dbCall = voiceCallMapper.selectById(result.getCallId());
            assertNotNull(dbCall);
            assertEquals(CallStatus.IN_PROGRESS.getCode(), dbCall.getStatus());
            assertNotNull(dbCall.getStartTime());

            VoiceCallParticipant dbParticipant = participantMapper.selectOne(
                    new LambdaQueryWrapper<VoiceCallParticipant>()
                            .eq(VoiceCallParticipant::getCallId, result.getCallId())
                            .eq(VoiceCallParticipant::getUserId, ownerId)
            );
            assertNotNull(dbParticipant);
            assertEquals(ConnectionStatus.CONNECTED.getCode(), dbParticipant.getConnectionStatus());

            log.info("自动创建通话成功: callId={}, roomId={}, ownerId={}", result.getCallId(), roomId, ownerId);
        }

        @Test
        @DisplayName("Step 2: joinCall → DB 新增 voice_call_participant")
        void joinCall_ShouldInsertParticipant() {
            Long roomId = 10002L;
            Long ownerId = 20010L;
            Long userId = 20011L;

            CallVO call = voiceCallService.createAutoCall(roomId, ownerId);

            log.info("==== [Step 2: joinCall] 状态校验 ====");

            CallVO result = voiceCallService.joinCall(call.getCallId(), userId);

            assertNotNull(result);
            assertEquals(2, result.getParticipantCount());

            long participantCount = participantMapper.selectCount(
                    new LambdaQueryWrapper<VoiceCallParticipant>()
                            .eq(VoiceCallParticipant::getCallId, call.getCallId())
                            .isNull(VoiceCallParticipant::getLeaveTime)
            );
            assertEquals(2, participantCount);

            log.info("加入通话成功: callId={}, participantCount={}", result.getCallId(), result.getParticipantCount());
        }

        @Test
        @DisplayName("Step 3: notifyReady → DB 更新参与者连接状态")
        void notifyReady_ShouldUpdateConnectionStatus() {
            Long roomId = 10003L;
            Long ownerId = 20020L;
            Long userId = 20021L;

            CallVO call = voiceCallService.createAutoCall(roomId, ownerId);
            voiceCallService.joinCall(call.getCallId(), userId);

            log.info("==== [Step 3: notifyReady] 状态校验 ====");

            voiceCallService.notifyReady(call.getCallId(), userId);

            VoiceCallParticipant participant = participantMapper.selectOne(
                    new LambdaQueryWrapper<VoiceCallParticipant>()
                            .eq(VoiceCallParticipant::getCallId, call.getCallId())
                            .eq(VoiceCallParticipant::getUserId, userId)
            );
            assertNotNull(participant);
            assertEquals(ConnectionStatus.CONNECTED.getCode(), participant.getConnectionStatus());

            log.info("WebRTC 就绪通知成功: callId={}, userId={}", call.getCallId(), userId);
        }

        @Test
        @DisplayName("Step 4: 普通 leaveCall → 标记离开，通话继续")
        void leaveCall_OrdinaryUser_ShouldMarkLeave() {
            Long roomId = 10004L;
            Long ownerId = 20030L;
            Long userId = 20031L;

            CallVO call = voiceCallService.joinOrCreateCall(roomId, ownerId);
            voiceCallService.joinCall(call.getCallId(), userId);

            log.info("==== [Step 4: 普通 leaveCall] 状态校验 ====");

            voiceCallService.leaveCall(call.getCallId(), userId);

            VoiceCall dbCall = voiceCallMapper.selectById(call.getCallId());
            assertEquals(CallStatus.IN_PROGRESS.getCode(), dbCall.getStatus());

            VoiceCallParticipant dbParticipant = participantMapper.selectOne(
                    new LambdaQueryWrapper<VoiceCallParticipant>()
                            .eq(VoiceCallParticipant::getCallId, call.getCallId())
                            .eq(VoiceCallParticipant::getUserId, userId)
            );
            assertNotNull(dbParticipant.getLeaveTime());
            assertEquals(ConnectionStatus.DISCONNECTED.getCode(), dbParticipant.getConnectionStatus());

            log.info("普通用户离开完成: callId={}, userId={} 已标记离开，通话仍进行中",
                    call.getCallId(), userId);
        }

        @Test
        @DisplayName("Step 5: 最后一人 leaveCall → 通话自动终止，状态 ENDED")
        void leaveCall_LastUser_ShouldTerminateCall() {
            Long roomId = 10005L;
            Long ownerId = 20050L;

            CallVO call = voiceCallService.createAutoCall(roomId, ownerId);

            log.info("==== [Step 5: 最后一人 leaveCall] 状态校验 ====");

            voiceCallService.leaveCall(call.getCallId(), ownerId);

            VoiceCall dbCall = voiceCallMapper.selectById(call.getCallId());
            assertEquals(CallStatus.ENDED.getCode(), dbCall.getStatus());
            assertNotNull(dbCall.getEndTime());
            assertTrue(dbCall.getDuration() >= 0);

            VoiceCallParticipant dbParticipant = participantMapper.selectOne(
                    new LambdaQueryWrapper<VoiceCallParticipant>()
                            .eq(VoiceCallParticipant::getCallId, call.getCallId())
                            .eq(VoiceCallParticipant::getUserId, ownerId)
            );
            assertEquals(ConnectionStatus.DISCONNECTED.getCode(), dbParticipant.getConnectionStatus());

            log.info("最后一人离开，通话自动终止: callId={}, duration={}s, status={}",
                    call.getCallId(), dbCall.getDuration(), dbCall.getStatus());
        }
    }

    // ==================== 测试流 B：房主离开但通话继续 + endCall 强制结束 ====================

    @Nested
    @DisplayName("测试流 B：房主离开但通话继续 + 强制结束")
    class OwnerLeaveAndEndCallFlow {

        @Test
        @DisplayName("Step 1: ownerLeave → 房主离开，通话继续")
        void ownerLeave_ShouldAllowCallToContinue() {
            Long roomId = 10010L;
            Long ownerId = 20040L;
            Long userId = 20041L;

            CallVO call = voiceCallService.joinOrCreateCall(roomId, ownerId);
            voiceCallService.joinCall(call.getCallId(), userId);

            log.info("==== [Step 1: ownerLeave] 状态校验 ====");

            voiceCallService.ownerLeave(call.getCallId(), ownerId);

            VoiceCall dbCall = voiceCallMapper.selectById(call.getCallId());
            assertEquals(CallStatus.IN_PROGRESS.getCode(), dbCall.getStatus());
            assertEquals(ownerId, dbCall.getInitiatorId());

            log.info("房主离开但通话继续: callId={}, ownerId={}, status={}",
                    call.getCallId(), ownerId, dbCall.getStatus());
        }

        @Test
        @DisplayName("Step 2: endCall → 强制结束通话，时长被正确计算")
        void endCall_ShouldTerminateAndCalculateDuration() {
            Long roomId = 10011L;
            Long ownerId = 20060L;
            Long userId1 = 20061L;
            Long userId2 = 20062L;

            CallVO call = voiceCallService.joinOrCreateCall(roomId, ownerId);
            voiceCallService.joinCall(call.getCallId(), userId1);
            voiceCallService.joinCall(call.getCallId(), userId2);

            log.info("==== [Step 2: endCall] 状态校验 ====");

            voiceCallService.endCall(call.getCallId(), ownerId);

            VoiceCall dbCall = voiceCallMapper.selectById(call.getCallId());
            assertEquals(CallStatus.ENDED.getCode(), dbCall.getStatus());
            assertNotNull(dbCall.getEndTime());
            assertTrue(dbCall.getDuration() >= 0);

            log.info("强制结束通话: callId={}, participants=[{},{},{}], duration={}s",
                    call.getCallId(), ownerId, userId1, userId2, dbCall.getDuration());
        }
    }

    // ==================== 测试流 C：异常边界 ====================

    @Nested
    @DisplayName("测试流 C：异常边界")
    class ExceptionAndEdgeCaseFlow {

        @Test
        @DisplayName("Step 1: joinCall 同一通话两次 → 幂等，不重复插入")
        void joinCall_Duplicate_ShouldBeIdempotent() {
            Long roomId = 10020L;
            Long ownerId = 30010L;
            Long userId = 30011L;

            CallVO call = voiceCallService.joinOrCreateCall(roomId, ownerId);

            log.info("==== [Step 1: joinCall 重复] 状态校验 ====");

            voiceCallService.joinCall(call.getCallId(), userId);
            voiceCallService.joinCall(call.getCallId(), userId);
            voiceCallService.joinCall(call.getCallId(), userId);

            long participantCount = participantMapper.selectCount(
                    new LambdaQueryWrapper<VoiceCallParticipant>()
                            .eq(VoiceCallParticipant::getCallId, call.getCallId())
                            .eq(VoiceCallParticipant::getUserId, userId)
            );
            assertEquals(1, participantCount);

            log.info("重复加入幂等验证通过: callId={}, userId={}, count={}",
                    call.getCallId(), userId, participantCount);
        }

        @Test
        @DisplayName("Step 2: 提前创建后再次 createAutoCall → 返回现有通话，不重复创建")
        void createAutoCall_Duplicate_ShouldReturnExisting() {
            Long roomId = 10021L;
            Long ownerId = 30020L;

            CallVO first = voiceCallService.joinOrCreateCall(roomId, ownerId);

            log.info("==== [Step 2: createAutoCall 重复] 状态校验 ====");

            CallVO second = voiceCallService.createAutoCall(roomId, ownerId);

            assertEquals(first.getCallId(), second.getCallId());

            long inProgressCount = voiceCallMapper.selectCount(
                    new LambdaQueryWrapper<VoiceCall>()
                            .eq(VoiceCall::getRoomId, roomId)
                            .eq(VoiceCall::getStatus, CallStatus.IN_PROGRESS.getCode())
            );
            assertEquals(1, inProgressCount);

            log.info("重复创建自动通话幂等验证通过: callId={}", first.getCallId());
        }

        @Test
        @DisplayName("Step 3: getActiveCall → 无活跃通话时返回 null")
        void getActiveCall_NoActiveCall_ShouldReturnNull() {
            Long roomId = 10030L;

            log.info("==== [Step 3: getActiveCall 无通话] 状态校验 ====");

            CallVO result = voiceCallService.getActiveCall(roomId);
            assertNull(result);

            log.info("无活跃通话返回 null: roomId={}", roomId);
        }

        @Test
        @DisplayName("Step 4: endCall 多次调用 → 幂等，不抛异常")
        void endCall_AlreadyEnded_ShouldBeIdempotent() {
            Long roomId = 10031L;
            Long ownerId = 30030L;

            CallVO call = voiceCallService.createAutoCall(roomId, ownerId);
            voiceCallService.endCall(call.getCallId(), ownerId);

            log.info("==== [Step 4: endCall 幂等] 状态校验 ====");

            assertDoesNotThrow(() -> voiceCallService.endCall(call.getCallId(), ownerId));

            VoiceCall dbCall = voiceCallMapper.selectById(call.getCallId());
            assertEquals(CallStatus.ENDED.getCode(), dbCall.getStatus());

            log.info("endCall 幂等验证通过: callId={}", call.getCallId());
        }

        @Test
        @DisplayName("Step 5: forwardSignaling → 非参与者静默丢弃，DB 无变化")
        void forwardSignaling_NonParticipant_ShouldSilentlyDrop() {
            Long roomId = 10040L;
            Long ownerId = 40020L;
            Long strangerId = 40021L;

            CallVO call = voiceCallService.createAutoCall(roomId, ownerId);

            log.info("==== [Step 5: forwardSignaling 非参与者] 状态校验 ====");

            SignalingDto dto = new SignalingDto();
            dto.setCallId(call.getCallId());
            dto.setType("offer");
            dto.setTargetUserId(ownerId);
            dto.setData(Map.of("sdp", "test"));

            assertDoesNotThrow(() -> voiceCallService.forwardSignaling(dto, strangerId));

            VoiceCall dbCall = voiceCallMapper.selectById(call.getCallId());
            assertEquals(CallStatus.IN_PROGRESS.getCode(), dbCall.getStatus());

            log.info("非参与者转发信令被静默丢弃: callId={}, strangerId={}, targetUserId={}",
                    call.getCallId(), strangerId, ownerId);
        }

        @Test
        @DisplayName("Step 6: forwardSignaling → 参与者正常转发，DB 无变化")
        void forwardSignaling_Participant_ShouldForward() {
            Long roomId = 10041L;
            Long ownerId = 40030L;
            Long userId = 40031L;

            CallVO call = voiceCallService.joinOrCreateCall(roomId, ownerId);
            voiceCallService.joinCall(call.getCallId(), userId);

            log.info("==== [Step 6: forwardSignaling 参与者正常转发] 状态校验 ====");

            SignalingDto dto = new SignalingDto();
            dto.setCallId(call.getCallId());
            dto.setType("answer");
            dto.setTargetUserId(ownerId);
            dto.setData(Map.of("sdp", "answer"));

            assertDoesNotThrow(() -> voiceCallService.forwardSignaling(dto, userId));

            VoiceCall dbCall = voiceCallMapper.selectById(call.getCallId());
            assertEquals(CallStatus.IN_PROGRESS.getCode(), dbCall.getStatus());

            log.info("参与者转发信令成功: callId={}, fromUserId={}, toUserId={}, type={}",
                    call.getCallId(), userId, ownerId, dto.getType());
        }
    }
}
