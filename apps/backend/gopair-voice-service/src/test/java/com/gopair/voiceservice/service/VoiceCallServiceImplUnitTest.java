package com.gopair.voiceservice.service;

import com.gopair.voiceservice.base.BaseIntegrationTest;
import com.gopair.voiceservice.domain.dto.SignalingDto;
import com.gopair.voiceservice.domain.po.VoiceCall;
import com.gopair.voiceservice.domain.po.VoiceCallParticipant;
import com.gopair.voiceservice.domain.vo.CallVO;
import com.gopair.voiceservice.enums.CallStatus;
import com.gopair.voiceservice.enums.CallType;
import com.gopair.voiceservice.enums.ConnectionStatus;
import com.gopair.voiceservice.mapper.VoiceCallMapper;
import com.gopair.voiceservice.mapper.VoiceCallParticipantMapper;
import com.gopair.voiceservice.service.VoiceCallService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 语音通话服务核心逻辑测试。
 *
 * * [核心策略]
 * - 真实 MySQL gopair_test：@Transactional 保证每个测试方法结束后自动回滚。
 * - WebSocket Mock：MockBean 替换真实推送，避免测试间相互干扰。
 * - 所有逻辑通过公开 API 路径验证，完全走真实 DB 操作。
 *
 * * [测试覆盖]
 * - 通话时长计算：提前结束/正常结束时长的正确性
 * - 幂等性：已结束的通话再次结束不报错
 * - 重复加入：同一用户多次加入/离开/再加入的状态流转
 * - 信令转发权限：只有参与者能转发信令，非参与者静默丢弃
 * - 自动创建：房间已有活跃通话时跳过创建
 *
 * @author gopair
 */
@Slf4j
@DisplayName("语音通话服务核心逻辑测试")
class VoiceCallServiceImplUnitTest extends BaseIntegrationTest {

    @Autowired
    private VoiceCallMapper voiceCallMapper;

    @Autowired
    private VoiceCallParticipantMapper participantMapper;

    @Autowired
    private VoiceCallService voiceCallService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM voice_call_participant");
        jdbcTemplate.update("DELETE FROM voice_call");
    }

    // no @AfterEach: @Transactional on @Nested classes handles rollback

    // ==================== 通话时长计算测试 ====================

    @Nested
    @DisplayName("通话时长计算")
    @Transactional
    class DurationCalculationTests {

        @Test
        @DisplayName("提前结束通话：时长为正数（等待 1 秒后结束）")
        void earlyEnd_ShouldCalculatePositiveDuration() {
            Long roomId = 79001L;
            Long ownerId = 79101L;
            CallVO call = voiceCallService.createAutoCall(roomId, ownerId);

            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {}

            voiceCallService.endCall(call.getCallId(), ownerId);

            Object[] row = selectCall(call.getCallId());
            assertEquals(CallStatus.ENDED.getCode(), row[4]);
            assertNotNull(row[6]);
            assertNotNull(row[7]);
            assertTrue((Integer) row[7] > 0, "duration must be positive, got: " + row[7]);
        }

        @Test
        @DisplayName("立即结束通话：时长为非负数")
        void immediateEnd_ShouldHaveZeroDuration() {
            Long roomId = 79002L;
            Long ownerId = 79201L;
            CallVO call = voiceCallService.createAutoCall(roomId, ownerId);

            voiceCallService.endCall(call.getCallId(), ownerId);

            Object[] row = selectCall(call.getCallId());
            assertEquals(CallStatus.ENDED.getCode(), row[4]);
            assertEquals(0, row[7], "immediate end duration must be exactly 0");
        }

        @Test
        @DisplayName("已结束的通话再次结束：幂等，不抛异常")
        void alreadyEnded_ShouldBeIdempotent() {
            Long roomId = 79003L;
            Long ownerId = 79301L;
            CallVO call = voiceCallService.createAutoCall(roomId, ownerId);
            voiceCallService.endCall(call.getCallId(), ownerId);

            assertDoesNotThrow(() -> voiceCallService.endCall(call.getCallId(), ownerId));

            VoiceCall dbCall = voiceCallMapper.selectById(call.getCallId());
            assertEquals(CallStatus.ENDED.getCode(), dbCall.getStatus());
        }
    }

    // ==================== 重复加入测试 ====================

    @Nested
    @DisplayName("重复加入同一通话")
    @Transactional
    class RejoinTests {

        @Test
        @DisplayName("首次加入 → 离开 → 再次加入：leaveTime 被清空，连接状态恢复")
        void rejoin_ShouldResetLeaveTimeAndConnectionStatus() {
            VoiceCall call = newCall(CallStatus.IN_PROGRESS.getCode());
            Long userId = 20022L;

            voiceCallService.joinOrCreateCall(call.getRoomId(), userId);

            VoiceCallParticipant p = findParticipant(call.getCallId(), userId);
            assertNotNull(p);
            assertNull(p.getLeaveTime());
            assertEquals(ConnectionStatus.CONNECTED.getCode(), p.getConnectionStatus());

            participantMapper.update(null,
                    new LambdaUpdateWrapper<VoiceCallParticipant>()
                            .eq(VoiceCallParticipant::getCallId, call.getCallId())
                            .eq(VoiceCallParticipant::getUserId, userId)
                            .set(VoiceCallParticipant::getLeaveTime, LocalDateTime.now().minusMinutes(1))
                            .set(VoiceCallParticipant::getConnectionStatus, ConnectionStatus.DISCONNECTED.getCode())
            );

            voiceCallService.joinOrCreateCall(call.getRoomId(), userId);

            VoiceCallParticipant rejoin = findParticipant(call.getCallId(), userId);
            assertNotNull(rejoin);
            assertNull(rejoin.getLeaveTime());
            assertEquals(ConnectionStatus.CONNECTED.getCode(), rejoin.getConnectionStatus());
        }

        @Test
        @DisplayName("同一用户多次 joinOrCreateCall：每次都幂等，不重复插入")
        void duplicateJoin_ShouldNotInsertDuplicate() {
            VoiceCall call = newCall(CallStatus.IN_PROGRESS.getCode());
            Long userId = 20031L;

            for (int i = 0; i < 3; i++) {
                voiceCallService.joinOrCreateCall(call.getRoomId(), userId);
            }

            long count = participantMapper.selectCount(
                    new LambdaQueryWrapper<VoiceCallParticipant>()
                            .eq(VoiceCallParticipant::getCallId, call.getCallId())
                            .eq(VoiceCallParticipant::getUserId, userId)
            );
            assertEquals(1, count);
        }
    }

    // ==================== 信令转发权限测试 ====================

    @Nested
    @DisplayName("信令转发权限校验")
    @Transactional
    class SignalingPermissionTests {

        @Test
        @DisplayName("参与者转发信令：DB 记录不变（只读查询）")
        void participantForwarding_ShouldNotModifyDb() {
            VoiceCall call = newCall(CallStatus.IN_PROGRESS.getCode());
            Long senderId = call.getInitiatorId();
            Long receiverId = 20112L;
            voiceCallService.joinCall(call.getCallId(), receiverId);

            long dbCountBefore = participantMapper.selectCount(
                    new LambdaQueryWrapper<VoiceCallParticipant>()
                            .eq(VoiceCallParticipant::getCallId, call.getCallId())
            );
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);

            SignalingDto dto = signalingDto(call.getCallId(), "offer", senderId, receiverId);
            voiceCallService.forwardSignaling(dto, senderId);

            VoiceCall dbCall = voiceCallMapper.selectById(call.getCallId());
            assertEquals(CallStatus.IN_PROGRESS.getCode(), dbCall.getStatus());
            assertTrue(dbCall.getStartTime().isAfter(before) || dbCall.getStartTime().equals(before));
            long dbCountAfter = participantMapper.selectCount(
                    new LambdaQueryWrapper<VoiceCallParticipant>()
                            .eq(VoiceCallParticipant::getCallId, call.getCallId())
            );
            assertEquals(dbCountBefore, dbCountAfter);
        }

        @Test
        @DisplayName("非参与者转发信令：静默丢弃，不抛异常")
        void nonParticipantForwarding_ShouldSilentlyDrop() {
            VoiceCall call = newCall(CallStatus.IN_PROGRESS.getCode());
            voiceCallService.joinCall(call.getCallId(), call.getInitiatorId());
            Long strangerId = 99999L;
            long dbCountBefore = participantMapper.selectCount(
                    new LambdaQueryWrapper<VoiceCallParticipant>()
                            .eq(VoiceCallParticipant::getCallId, call.getCallId())
            );

            SignalingDto dto = signalingDto(call.getCallId(), "offer", strangerId, call.getInitiatorId());
            assertDoesNotThrow(() -> voiceCallService.forwardSignaling(dto, strangerId));

            long dbCountAfter = participantMapper.selectCount(
                    new LambdaQueryWrapper<VoiceCallParticipant>()
                            .eq(VoiceCallParticipant::getCallId, call.getCallId())
            );
            assertEquals(dbCountBefore, dbCountAfter);
        }
    }

    // ==================== 自动创建通话幂等测试 ====================

    @Nested
    @DisplayName("自动创建通话幂等性")
    @Transactional
    class AutoCreateIdempotentTests {

        @Test
        @DisplayName("房间已有活跃通话时跳过创建，直接返回现有通话")
        void existingActiveCall_ShouldSkipCreation() {
            Long roomId = 99901L;
            Long roomOwnerId = 99901L;

            CallVO first = voiceCallService.createAutoCall(roomId, roomOwnerId);
            assertNotNull(first);
            assertEquals(roomId, first.getRoomId());

            Long firstCallId = first.getCallId();

            CallVO second = voiceCallService.createAutoCall(roomId, roomOwnerId);
            assertEquals(firstCallId, second.getCallId());

            long inProgressCount = voiceCallMapper.selectCount(
                    new LambdaQueryWrapper<VoiceCall>()
                            .eq(VoiceCall::getRoomId, roomId)
                            .eq(VoiceCall::getStatus, CallStatus.IN_PROGRESS.getCode())
            );
            assertEquals(1, inProgressCount);
        }
    }

    // ==================== 辅助方法 ====================

    private VoiceCall newCall(int status) {
        return newCall(LocalDateTime.now(), status);
    }

    private VoiceCall newCall(LocalDateTime startTime, int status) {
        VoiceCall call = new VoiceCall();
        call.setRoomId(System.nanoTime() % 100000L + 10000L);
        call.setInitiatorId(20000L + (System.nanoTime() % 10000L));
        call.setCallType(CallType.MULTI_USER.getCode());
        call.setStatus(status);
        call.setStartTime(startTime);
        call.setIsAutoCreated(false);
        voiceCallMapper.insert(call);
        return call;
    }

    private VoiceCallParticipant findParticipant(Long callId, Long userId) {
        return participantMapper.selectOne(
                new LambdaQueryWrapper<VoiceCallParticipant>()
                        .eq(VoiceCallParticipant::getCallId, callId)
                        .eq(VoiceCallParticipant::getUserId, userId)
        );
    }

    private SignalingDto signalingDto(Long callId, String type, Long senderId, Long targetUserId) {
        SignalingDto dto = new SignalingDto();
        dto.setCallId(callId);
        dto.setType(type);
        dto.setTargetUserId(targetUserId);
        dto.setData(Map.of("sdp", "test"));
        return dto;
    }
}
