package com.gopair.voiceservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.framework.logging.annotation.LogRecord;
import com.gopair.voiceservice.domain.dto.SignalingDto;
import com.gopair.voiceservice.domain.po.VoiceCall;
import com.gopair.voiceservice.domain.po.VoiceCallParticipant;
import com.gopair.voiceservice.domain.vo.CallParticipantVO;
import com.gopair.voiceservice.domain.vo.CallVO;
import com.gopair.voiceservice.enums.CallStatus;
import com.gopair.voiceservice.enums.CallType;
import com.gopair.voiceservice.enums.ConnectionStatus;
import com.gopair.voiceservice.mapper.VoiceCallMapper;
import com.gopair.voiceservice.mapper.VoiceCallParticipantMapper;
import com.gopair.voiceservice.service.VoiceCallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 语音通话服务实现
 * 采用按需创建 + 两阶段加入模式：
 *   1. joinOrCreateCall  — 创建/加入通话，记录参与者，但不广播 participant-join
 *   2. notifyReady       — 前端 WebRTC 就绪后调用，广播 participant-join
 * 这样可以避免其他参与者在当前用户 WebRTC 未就绪时就发送 offer 导致信令丢失。
 *
 * @author gopair
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceCallServiceImpl implements VoiceCallService {

    private final VoiceCallMapper voiceCallMapper;
    private final VoiceCallParticipantMapper participantMapper;
    private final WebSocketMessageProducer wsProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "自动创建语音通话", module = "语音通话")
    public CallVO createAutoCall(Long roomId, Long roomOwnerId) {
        VoiceCall existing = voiceCallMapper.selectOne(
                new LambdaQueryWrapper<VoiceCall>()
                        .eq(VoiceCall::getRoomId, roomId)
                        .eq(VoiceCall::getStatus, CallStatus.IN_PROGRESS.getCode())
                        .last("LIMIT 1")
        );
        if (existing != null) {
            log.info("[语音] 房间已有活跃通话，跳过自动创建: roomId={}", roomId);
            return buildCallVO(existing);
        }

        VoiceCall call = new VoiceCall();
        call.setRoomId(roomId);
        call.setInitiatorId(roomOwnerId);
        call.setCallType(CallType.MULTI_USER.getCode());
        call.setStatus(CallStatus.IN_PROGRESS.getCode());
        call.setStartTime(LocalDateTime.now());
        call.setIsAutoCreated(true);

        try {
            voiceCallMapper.insert(call);
        } catch (DuplicateKeyException e) {
            // 并发竞态：另一线程/用户已创建通话，复用现有记录
            log.info("[语音] 并发竞态，房间通话已由另一方创建，复用现有通话: roomId={}", roomId);
            VoiceCall conflict = voiceCallMapper.selectOne(
                    new LambdaQueryWrapper<VoiceCall>()
                            .eq(VoiceCall::getRoomId, roomId)
                            .eq(VoiceCall::getStatus, CallStatus.IN_PROGRESS.getCode())
                            .last("LIMIT 1")
            );
            return buildCallVO(conflict);
        }

        addOrUpdateParticipant(call.getCallId(), roomOwnerId);

        log.info("[语音] 房间通话自动创建: callId={}, roomId={}, ownerId={}",
                call.getCallId(), roomId, roomOwnerId);
        return buildCallVO(call);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "用户加入通话", module = "语音通话")
    public CallVO joinCall(Long callId, Long userId) {
        VoiceCall call = getCallOrThrow(callId);

        if (call.getStatus() != CallStatus.IN_PROGRESS.getCode()) {
            throw new IllegalStateException("通话已结束，无法加入");
        }

        boolean wasActive = participantMapper.selectCount(
                new LambdaQueryWrapper<VoiceCallParticipant>()
                        .eq(VoiceCallParticipant::getCallId, callId)
                        .eq(VoiceCallParticipant::getUserId, userId)
                        .isNull(VoiceCallParticipant::getLeaveTime)
        ) > 0;

        addOrUpdateParticipant(callId, userId);

        log.info("[语音] 用户加入通话（等待就绪信号）: callId={}, userId={}, wasActive={}",
                callId, userId, wasActive);
        return buildCallVO(call);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "创建或加入通话", module = "语音通话")
    public CallVO joinOrCreateCall(Long roomId, Long userId) {
        VoiceCall call = voiceCallMapper.selectOne(
                new LambdaQueryWrapper<VoiceCall>()
                        .eq(VoiceCall::getRoomId, roomId)
                        .eq(VoiceCall::getStatus, CallStatus.IN_PROGRESS.getCode())
                        .orderByDesc(VoiceCall::getStartTime)
                        .last("LIMIT 1")
        );

        if (call == null) {
            call = new VoiceCall();
            call.setRoomId(roomId);
            call.setInitiatorId(userId);
            call.setCallType(CallType.MULTI_USER.getCode());
            call.setStatus(CallStatus.IN_PROGRESS.getCode());
            call.setStartTime(LocalDateTime.now());
            call.setIsAutoCreated(false);

            try {
                voiceCallMapper.insert(call);
            } catch (DuplicateKeyException e) {
                // 并发竞态：另一用户已创建通话，复用现有记录
                log.info("[语音] 并发竞态，房间通话已由另一用户创建，复用现有通话: roomId={}", roomId);
                VoiceCall conflict = voiceCallMapper.selectOne(
                        new LambdaQueryWrapper<VoiceCall>()
                                .eq(VoiceCall::getRoomId, roomId)
                                .eq(VoiceCall::getStatus, CallStatus.IN_PROGRESS.getCode())
                                .orderByDesc(VoiceCall::getStartTime)
                                .last("LIMIT 1")
                );
                if (conflict == null) {
                    throw new IllegalStateException("通话竞态恢复失败，请重试: roomId=" + roomId);
                }
                call = conflict;
            }

            final Long callId = call.getCallId();
            wsProducer.sendEventToRoom(roomId, "call_start", Map.of(
                    "callId", callId,
                    "roomId", roomId,
                    "initiatorId", userId
            ));
        }

        return joinCall(call.getCallId(), userId);
    }

    @Override
    @LogRecord(operation = "用户通话就绪", module = "语音通话")
    public void notifyReady(Long callId, Long userId) {
        VoiceCall call = getCallOrThrow(callId);
        wsProducer.sendEventToRoom(call.getRoomId(), "voice_roster_update", Map.of(
                "callId", callId
        ));
        log.info("[语音] 用户就绪，广播 voice_roster_update: callId={}, userId={}", callId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "用户离开通话", module = "语音通话")
    public void leaveCall(Long callId, Long userId) {
        VoiceCallParticipant participant = participantMapper.selectOne(
                new LambdaQueryWrapper<VoiceCallParticipant>()
                        .eq(VoiceCallParticipant::getCallId, callId)
                        .eq(VoiceCallParticipant::getUserId, userId)
                        .isNull(VoiceCallParticipant::getLeaveTime)
        );
        if (participant != null) {
            participant.setLeaveTime(LocalDateTime.now());
            participant.setConnectionStatus(ConnectionStatus.DISCONNECTED.getCode());
            participantMapper.updateById(participant);
        }

        // 一次性查询剩余活跃参与者（当前用户已被标记离开），避免两次查询的竞态窗口
        List<Long> remaining = getActiveParticipantUserIds(callId).stream()
                .filter(uid -> !uid.equals(userId))
                .toList();

        VoiceCall call = getCallOrThrow(callId);
        if (remaining.isEmpty()) {
            terminateCall(call);
            wsProducer.sendEventToRoom(call.getRoomId(), "call_end", Map.of(
                    "callId", callId,
                    "roomId", call.getRoomId()
            ));
        } else {
            wsProducer.sendEventToRoom(call.getRoomId(), "voice_roster_update", Map.of(
                    "callId", callId
            ));
        }

        log.info("[语音] 用户离开通话: callId={}, userId={}", callId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "房主退出通话", module = "语音通话")
    public void ownerLeave(Long callId, Long userId) {
        VoiceCallParticipant participant = participantMapper.selectOne(
                new LambdaQueryWrapper<VoiceCallParticipant>()
                        .eq(VoiceCallParticipant::getCallId, callId)
                        .eq(VoiceCallParticipant::getUserId, userId)
                        .isNull(VoiceCallParticipant::getLeaveTime)
        );
        if (participant != null) {
            participant.setLeaveTime(LocalDateTime.now());
            participant.setConnectionStatus(ConnectionStatus.DISCONNECTED.getCode());
            participantMapper.updateById(participant);
        }

        VoiceCall call = getCallOrThrow(callId);
        wsProducer.sendEventToRoom(call.getRoomId(), "voice_roster_update", Map.of(
                "callId", callId
        ));
        log.info("[语音] 房主退出通话（通话继续）: callId={}, ownerId={}", callId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "结束通话", module = "语音通话")
    public void endCall(Long callId, Long userId) {
        VoiceCall call = getCallOrThrow(callId);
        if (call.getStatus() != CallStatus.IN_PROGRESS.getCode()) {
            log.warn("[语音] 通话已结束，跳过 endCall: callId={}, status={}", callId, call.getStatus());
            return;
        }

        List<Long> activeUserIds = getActiveParticipantUserIds(callId);
        for (Long uid : activeUserIds) {
            try {
                wsProducer.sendSignalingMessage(uid, Map.of(
                        "type", "call-end",
                        "callId", callId,
                        "fromUserId", userId
                ));
            } catch (Exception e) {
                log.warn("[语音] 发送通话结束信令失败: callId={}, targetUserId={}", callId, uid, e);
            }
        }

        terminateCall(call);

        wsProducer.sendEventToRoom(call.getRoomId(), "call_end", Map.of(
                "callId", callId,
                "roomId", call.getRoomId()
        ));

        log.info("[语音] 通话结束: callId={}, operatorId={}", callId, userId);
    }

    @Override
    public CallVO getCall(Long callId) {
        return buildCallVO(getCallOrThrow(callId));
    }

    @Override
    public CallVO getActiveCall(Long roomId) {
        VoiceCall call = voiceCallMapper.selectOne(
                new LambdaQueryWrapper<VoiceCall>()
                        .eq(VoiceCall::getRoomId, roomId)
                        .eq(VoiceCall::getStatus, CallStatus.IN_PROGRESS.getCode())
                        .orderByDesc(VoiceCall::getStartTime)
                        .last("LIMIT 1")
        );
        return call != null ? buildCallVO(call) : null;
    }

    @Override
    @LogRecord(operation = "转发通话信令", module = "语音通话")
    public void forwardSignaling(SignalingDto dto, Long fromUserId) {
        boolean isParticipant = participantMapper.selectCount(
                new LambdaQueryWrapper<VoiceCallParticipant>()
                        .eq(VoiceCallParticipant::getCallId, dto.getCallId())
                        .eq(VoiceCallParticipant::getUserId, fromUserId)
                        .isNull(VoiceCallParticipant::getLeaveTime)
        ) > 0;
        if (!isParticipant) {
            log.warn("[语音] 信令转发权限校验失败，非通话参与者: callId={}, fromUserId={}",
                    dto.getCallId(), fromUserId);
            return;
        }

        wsProducer.sendSignalingMessage(dto.getTargetUserId(), Map.of(
                "type", dto.getType(),
                "callId", dto.getCallId(),
                "fromUserId", fromUserId,
                "data", dto.getData() != null ? dto.getData() : Map.of()
        ));
        log.info("[语音] 信令转发: type={}, callId={}, from={}, to={}",
                dto.getType(), dto.getCallId(), fromUserId, dto.getTargetUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanupRoomVoiceCalls(Long roomId) {
        List<VoiceCall> calls = voiceCallMapper.selectByRoomId(roomId);
        if (calls.isEmpty()) {
            return 0;
        }
        List<Long> callIds = calls.stream().map(VoiceCall::getCallId).toList();
        if (!callIds.isEmpty()) {
            participantMapper.deleteByCallIds(callIds);
        }
        voiceCallMapper.deleteByRoomId(roomId);
        log.info("[语音服务] 清理房间{}的语音通话 {} 条", roomId, calls.size());
        return calls.size();
    }

    @Override
    public int endAllCallsInRoom(Long roomId) {
        List<VoiceCall> activeCalls = voiceCallMapper.selectList(
                new LambdaQueryWrapper<VoiceCall>()
                        .eq(VoiceCall::getRoomId, roomId)
                        .eq(VoiceCall::getStatus, CallStatus.IN_PROGRESS.getCode())
        );

        if (activeCalls.isEmpty()) {
            log.info("[语音服务] 房间{}无活跃通话，跳过终止", roomId);
            return 0;
        }

        int count = 0;
        for (VoiceCall call : activeCalls) {
            if (call.getStatus() == CallStatus.IN_PROGRESS.getCode()) {
                call.setStatus(CallStatus.ENDED.getCode());
                call.setEndTime(LocalDateTime.now());
                if (call.getStartTime() != null) {
                    long seconds = java.time.Duration.between(call.getStartTime(), call.getEndTime()).getSeconds();
                    call.setDuration((int) Math.max(0, seconds));
                }
                voiceCallMapper.updateById(call);
            }

            List<Long> activeUserIds = getActiveParticipantUserIds(call.getCallId());
            for (Long uid : activeUserIds) {
                try {
                    wsProducer.sendSignalingMessage(uid, Map.of(
                            "type", "call-end",
                            "callId", call.getCallId()
                    ));
                } catch (Exception e) {
                    log.warn("[语音服务] 发送 call-end 信令失败: callId={}, userId={}", call.getCallId(), uid, e);
                }
            }

            wsProducer.sendEventToRoom(roomId, "call_end", Map.of(
                    "callId", call.getCallId(),
                    "roomId", roomId
            ));

            count++;
            log.info("[语音服务] 房间{}下通话{}已终止", roomId, call.getCallId());
        }

        log.info("[语音服务] 房间{}共终止{}个活跃通话", roomId, count);
        return count;
    }

    // -------------------------------------------------------------------------
    // 私有工具方法
    // -------------------------------------------------------------------------

    private void addOrUpdateParticipant(Long callId, Long userId) {
        VoiceCallParticipant existing = participantMapper.selectOne(
                new LambdaQueryWrapper<VoiceCallParticipant>()
                        .eq(VoiceCallParticipant::getCallId, callId)
                        .eq(VoiceCallParticipant::getUserId, userId)
        );

        if (existing != null) {
            participantMapper.update(
                    null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<VoiceCallParticipant>()
                            .eq(VoiceCallParticipant::getId, existing.getId())
                            .set(VoiceCallParticipant::getJoinTime, LocalDateTime.now())
                            .set(VoiceCallParticipant::getLeaveTime, null)
                            .set(VoiceCallParticipant::getConnectionStatus, ConnectionStatus.CONNECTED.getCode())
            );
        } else {
            VoiceCallParticipant p = new VoiceCallParticipant();
            p.setCallId(callId);
            p.setUserId(userId);
            p.setJoinTime(LocalDateTime.now());
            p.setConnectionStatus(ConnectionStatus.CONNECTED.getCode());
            try {
                participantMapper.insert(p);
            } catch (DuplicateKeyException e) {
                // 并发竞态：另一线程已插入，降级为更新
                participantMapper.update(
                        null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<VoiceCallParticipant>()
                                .eq(VoiceCallParticipant::getCallId, callId)
                                .eq(VoiceCallParticipant::getUserId, userId)
                                .set(VoiceCallParticipant::getJoinTime, LocalDateTime.now())
                                .set(VoiceCallParticipant::getLeaveTime, null)
                                .set(VoiceCallParticipant::getConnectionStatus, ConnectionStatus.CONNECTED.getCode())
                );
            }
        }
    }

    private void terminateCall(VoiceCall call) {
        if (call.getStatus() == CallStatus.IN_PROGRESS.getCode()) {
            call.setStatus(CallStatus.ENDED.getCode());
            call.setEndTime(LocalDateTime.now());
            if (call.getStartTime() != null) {
                long seconds = java.time.Duration.between(call.getStartTime(), call.getEndTime()).getSeconds();
                call.setDuration((int) Math.max(0, seconds));
            }
            voiceCallMapper.updateById(call);
        }
    }

    private List<Long> getActiveParticipantUserIds(Long callId) {
        return participantMapper.selectList(
                new LambdaQueryWrapper<VoiceCallParticipant>()
                        .eq(VoiceCallParticipant::getCallId, callId)
                        .isNull(VoiceCallParticipant::getLeaveTime)
        ).stream().map(VoiceCallParticipant::getUserId).toList();
    }

    private VoiceCall getCallOrThrow(Long callId) {
        VoiceCall call = voiceCallMapper.selectById(callId);
        if (call == null) {
            throw new IllegalArgumentException("通话不存在或已结束");
        }
        return call;
    }

    private CallVO buildCallVO(VoiceCall call) {
        List<VoiceCallParticipant> participants = participantMapper.selectList(
                new LambdaQueryWrapper<VoiceCallParticipant>()
                        .eq(VoiceCallParticipant::getCallId, call.getCallId())
                        .isNull(VoiceCallParticipant::getLeaveTime)
        );

        List<CallParticipantVO> participantVOs = participants.stream().map(p -> {
            CallParticipantVO vo = new CallParticipantVO();
            vo.setUserId(p.getUserId());
            vo.setJoinTime(p.getJoinTime());
            vo.setLeaveTime(p.getLeaveTime());
            vo.setConnectionStatus(p.getConnectionStatus());
            vo.setInitiator(p.getUserId().equals(call.getInitiatorId()));
            return vo;
        }).toList();

        CallVO vo = new CallVO();
        vo.setCallId(call.getCallId());
        vo.setRoomId(call.getRoomId());
        vo.setInitiatorId(call.getInitiatorId());
        vo.setCallType(call.getCallType());
        vo.setStatus(call.getStatus());
        vo.setStartTime(call.getStartTime());
        vo.setEndTime(call.getEndTime());
        // 用实际活跃参与者数量覆盖累计计数，避免重复加入导致数字虚高
        vo.setParticipantCount(participantVOs.size());
        vo.setJoinable(call.getStatus() == CallStatus.IN_PROGRESS.getCode());
        vo.setParticipants(participantVOs);
        return vo;
    }
}
