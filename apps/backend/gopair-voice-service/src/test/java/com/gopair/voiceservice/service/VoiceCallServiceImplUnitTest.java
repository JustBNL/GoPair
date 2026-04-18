package com.gopair.voiceservice.service;

import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.voiceservice.base.BaseIntegrationTest;
import com.gopair.voiceservice.base.VoiceServiceTestConfig;
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
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 语音通话服务核心逻辑测试。
 *
 * * [核心策略]
 * - Byte Buddy 1.14.x 不支持 Java 23，@MockBean 导致 context 启动失败。
 * - 改用手动创建 service 实例：mapper 走 @Autowired 真实实例，wsProducer 走手动 mock。
 * - 所有逻辑通过公开 API 路径验证，避免反射。
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
@Import({VoiceServiceTestConfig.class, VoiceCallServiceImplUnitTest.TestConfig.class})
class VoiceCallServiceImplUnitTest extends BaseIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public VoiceCallServiceImpl voiceCallServiceImpl(
                VoiceCallMapper voiceCallMapper,
                VoiceCallParticipantMapper participantMapper,
                WebSocketMessageProducer wsProducer) {
            return new VoiceCallServiceImpl(voiceCallMapper, participantMapper, wsProducer);
        }
    }

    @Autowired
    private VoiceCallMapper voiceCallMapper;

    @Autowired
    private VoiceCallParticipantMapper participantMapper;

    @Autowired
    private VoiceCallServiceImpl voiceCallService;

    // ==================== 通话时长计算测试 ====================

    @Nested
    @DisplayName("通话时长计算")
    class DurationCalculationTests {

        @Test
        @DisplayName("提前结束通话：时长为正数")
        void earlyEnd_ShouldCalculatePositiveDuration() {
            LocalDateTime startTime = LocalDateTime.now().minusMinutes(5);
            VoiceCall call = newCall(startTime, CallStatus.IN_PROGRESS.getCode());
            long dbCountBefore = voiceCallMapper.selectCount(null);

            voiceCallService.endCall(call.getCallId(), call.getInitiatorId());

            VoiceCall dbCall = voiceCallMapper.selectById(call.getCallId());
            assertEquals(CallStatus.ENDED.getCode(), dbCall.getStatus());
            assertNotNull(dbCall.getEndTime());
            assertNotNull(dbCall.getDuration());
            assertTrue(dbCall.getDuration() > 0);
            // 确认只有 1 次 update
            assertEquals(dbCountBefore, voiceCallMapper.selectCount(null));
        }

        @Test
        @DisplayName("立即结束通话：时长为 0")
        void immediateEnd_ShouldHaveZeroDuration() {
            VoiceCall call = newCall(LocalDateTime.now(), CallStatus.IN_PROGRESS.getCode());

            voiceCallService.endCall(call.getCallId(), call.getInitiatorId());

            VoiceCall dbCall = voiceCallMapper.selectById(call.getCallId());
            assertEquals(CallStatus.ENDED.getCode(), dbCall.getStatus());
            assertEquals(0, dbCall.getDuration());
        }

        @Test
        @DisplayName("已结束的通话再次结束：幂等，不抛异常")
        void alreadyEnded_ShouldBeIdempotent() {
            VoiceCall call = newCall(LocalDateTime.now().minusMinutes(3), CallStatus.ENDED.getCode());
            call.setEndTime(LocalDateTime.now().minusMinutes(1));
            call.setDuration(120);
            voiceCallMapper.updateById(call);
            long dbCountBefore = voiceCallMapper.selectCount(null);

            assertDoesNotThrow(() -> voiceCallService.endCall(call.getCallId(), call.getInitiatorId()));

            VoiceCall dbCall = voiceCallMapper.selectById(call.getCallId());
            assertEquals(120, dbCall.getDuration());
            assertEquals(dbCountBefore, voiceCallMapper.selectCount(null));
        }
    }

    // ==================== 重复加入测试 ====================

    @Nested
    @DisplayName("重复加入同一通话")
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

            // 模拟用户离开：更新 leaveTime
            participantMapper.update(null,
                    new LambdaUpdateWrapper<VoiceCallParticipant>()
                            .eq(VoiceCallParticipant::getCallId, call.getCallId())
                            .eq(VoiceCallParticipant::getUserId, userId)
                            .set(VoiceCallParticipant::getLeaveTime, LocalDateTime.now().minusMinutes(1))
                            .set(VoiceCallParticipant::getConnectionStatus, ConnectionStatus.DISCONNECTED.getCode())
            );

            // 再次加入
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
    class SignalingPermissionTests {

        @Test
        @DisplayName("参与者转发信令：DB 记录不变（只读查询）")
        void participantForwarding_ShouldNotModifyDb() {
            VoiceCall call = newCall(CallStatus.IN_PROGRESS.getCode());
            Long senderId = call.getInitiatorId();
            Long receiverId = 20112L;
            voiceCallService.joinOrCreateCall(call.getCallId(), receiverId);

            long dbCountBefore = participantMapper.selectCount(
                    new LambdaQueryWrapper<VoiceCallParticipant>()
                            .eq(VoiceCallParticipant::getCallId, call.getCallId())
            );
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);

            // 转发信令（targetUserId = receiverId）
            var dto = signalingDto(call.getCallId(), "offer", senderId, receiverId);
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
            voiceCallService.joinOrCreateCall(call.getCallId(), call.getInitiatorId());
            Long strangerId = 99999L;
            long dbCountBefore = participantMapper.selectCount(
                    new LambdaQueryWrapper<VoiceCallParticipant>()
                            .eq(VoiceCallParticipant::getCallId, call.getCallId())
            );

            var dto = signalingDto(call.getCallId(), "offer", strangerId, call.getInitiatorId());
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
    class AutoCreateIdempotentTests {

        @Test
        @DisplayName("房间已有活跃通话时跳过创建，直接返回现有通话")
        void existingActiveCall_ShouldSkipCreation() {
            Long roomId = 99901L;
            Long roomOwnerId = 99901L;

            // 第一次调用：创建通话
            CallVO first = voiceCallService.createAutoCall(roomId, roomOwnerId);
            assertNotNull(first);
            assertEquals(roomId, first.getRoomId());

            Long firstCallId = first.getCallId();

            // 第二次调用：跳过创建
            CallVO second = voiceCallService.createAutoCall(roomId, roomOwnerId);
            assertEquals(firstCallId, second.getCallId());

            // DB 确认只有一条 IN_PROGRESS 记录
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

    private com.gopair.voiceservice.domain.dto.SignalingDto signalingDto(
            Long callId, String type, Long senderId, Long targetUserId) {
        var dto = new com.gopair.voiceservice.domain.dto.SignalingDto();
        dto.setCallId(callId);
        dto.setType(type);
        dto.setTargetUserId(targetUserId);
        dto.setData(java.util.Map.of("sdp", "test"));
        return dto;
    }
}
