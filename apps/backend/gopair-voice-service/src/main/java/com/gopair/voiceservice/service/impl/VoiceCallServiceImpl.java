package com.gopair.voiceservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.voiceservice.domain.dto.SignalingDto;
import com.gopair.voiceservice.domain.po.VoiceCall;
import com.gopair.voiceservice.domain.po.VoiceCallParticipant;
import com.gopair.voiceservice.domain.vo.CallParticipantVO;
import com.gopair.voiceservice.domain.vo.CallVO;
import com.gopair.voiceservice.enums.CallStatus;
import com.gopair.voiceservice.mapper.VoiceCallMapper;
import com.gopair.voiceservice.mapper.VoiceCallParticipantMapper;
import com.gopair.voiceservice.service.VoiceCallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    @Transactional
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
        call.setCallType(2);
        call.setStatus(CallStatus.IN_PROGRESS.getCode());
        call.setStartTime(LocalDateTime.now());
        call.setParticipantCount(1);
        call.setIsAutoCreated(true);
        voiceCallMapper.insert(call);

        addOrUpdateParticipant(call.getCallId(), roomOwnerId);

        log.info("[语音] 房间通话自动创建: callId={}, roomId={}, ownerId={}",
                call.getCallId(), roomId, roomOwnerId);
        return buildCallVO(call);
    }

    /**
     * 加入通话（仅记录参与者，不广播 participant-join）
     * participant-join 广播由 notifyReady 在前端 WebRTC 就绪后触发。
     */
    @Override
    @Transactional
    public CallVO joinCall(Long callId, Long userId) {
        VoiceCall call = getCallOrThrow(callId);

        if (call.getStatus() != CallStatus.IN_PROGRESS.getCode()) {
            throw new IllegalStateException("通话已结束，无法加入");
        }

        // 清理同用户在此通话中可能残留的僵尸参与者记录（异常退出未调用 leaveCall 时产生）
        VoiceCallParticipant zombie = participantMapper.selectOne(
                new LambdaQueryWrapper<VoiceCallParticipant>()
                        .eq(VoiceCallParticipant::getCallId, callId)
                        .eq(VoiceCallParticipant::getUserId, userId)
                        .isNull(VoiceCallParticipant::getLeaveTime)
        );
        if (zombie != null) {
            zombie.setLeaveTime(LocalDateTime.now());
            zombie.setConnectionStatus(2);
            participantMapper.updateById(zombie);
            call.setParticipantCount(Math.max(0, call.getParticipantCount() - 1));
            voiceCallMapper.updateById(call);
            log.info("[语音] 清理僵尸参与者记录后重新加入: callId={}, userId={}", callId, userId);
        }

        addOrUpdateParticipant(callId, userId);
        call.setParticipantCount(call.getParticipantCount() + 1);
        voiceCallMapper.updateById(call);

        // 注意：此处不广播 participant-join，由 notifyReady 在前端就绪后触发
        log.info("[语音] 用户加入通话（等待就绪信号）: callId={}, userId={}", callId, userId);
        return buildCallVO(call);
    }

    @Override
    @Transactional
    public CallVO joinOrCreateCall(Long roomId, Long userId) {
        VoiceCall call = voiceCallMapper.selectOne(
                new LambdaQueryWrapper<VoiceCall>()
                        .eq(VoiceCall::getRoomId, roomId)
                        .eq(VoiceCall::getStatus, CallStatus.IN_PROGRESS.getCode())
                        .orderByDesc(VoiceCall::getStartTime)
                        .last("LIMIT 1")
        );

        if (call == null) {
            // 无活跃通话，当前用户为发起人，按需创建
            call = new VoiceCall();
            call.setRoomId(roomId);
            call.setInitiatorId(userId);
            call.setCallType(2);
            call.setStatus(CallStatus.IN_PROGRESS.getCode());
            call.setStartTime(LocalDateTime.now());
            call.setParticipantCount(0);
            call.setIsAutoCreated(false);
            voiceCallMapper.insert(call);
            log.info("[语音] 按需创建通话: callId={}, roomId={}, initiatorId={}",
                    call.getCallId(), roomId, userId);

            // 广播 call_start 给房间内其他成员（通知有新通话开始）
            final Long callId = call.getCallId();
            wsProducer.sendEventToRoom(roomId, "call_start", Map.of(
                    "callId", callId,
                    "roomId", roomId,
                    "initiatorId", userId
            ));
        }

        // 加入通话（不广播 participant-join，等待 notifyReady）
        return joinCall(call.getCallId(), userId);
    }

    /**
     * 前端 WebRTC 就绪后调用，向通话中其他参与者广播 participant-join。
     * 此时当前用户的 PeerConnection 已建立，其他人发来的 offer 不会丢失。
     */
    @Override
    public void notifyReady(Long callId, Long userId) {
        // 向其他活跃参与者广播 participant-join
        getActiveParticipantUserIds(callId).stream()
                .filter(uid -> !uid.equals(userId))
                .forEach(uid -> wsProducer.sendSignalingMessage(uid, Map.of(
                        "type", "participant-join",
                        "callId", callId,
                        "userId", userId
                )));
        log.info("[语音] 用户 WebRTC 就绪，广播 participant-join: callId={}, userId={}", callId, userId);
    }

    @Override
    @Transactional
    public void leaveCall(Long callId, Long userId) {
        VoiceCallParticipant participant = participantMapper.selectOne(
                new LambdaQueryWrapper<VoiceCallParticipant>()
                        .eq(VoiceCallParticipant::getCallId, callId)
                        .eq(VoiceCallParticipant::getUserId, userId)
                        .isNull(VoiceCallParticipant::getLeaveTime)
        );
        if (participant != null) {
            participant.setLeaveTime(LocalDateTime.now());
            participant.setConnectionStatus(2);
            participantMapper.updateById(participant);
        }

        getActiveParticipantUserIds(callId).stream()
                .filter(uid -> !uid.equals(userId))
                .forEach(uid -> wsProducer.sendSignalingMessage(uid, Map.of(
                        "type", "participant-leave",
                        "callId", callId,
                        "userId", userId
                )));

        if (getActiveParticipantUserIds(callId).isEmpty()) {
            VoiceCall call = getCallOrThrow(callId);
            terminateCall(call);
            wsProducer.sendEventToRoom(call.getRoomId(), "call_end", Map.of(
                    "callId", callId,
                    "roomId", call.getRoomId()
            ));
        }

        log.info("[语音] 用户离开通话: callId={}, userId={}", callId, userId);
    }

    @Override
    @Transactional
    public void endCall(Long callId, Long userId) {
        VoiceCall call = getCallOrThrow(callId);

        getActiveParticipantUserIds(callId)
                .forEach(uid -> wsProducer.sendSignalingMessage(uid, Map.of(
                        "type", "call-end",
                        "callId", callId,
                        "fromUserId", userId
                )));

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
    public CallVO getActiveCall(Long roomId, Long userId) {
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
    public void forwardSignaling(SignalingDto dto, Long fromUserId) {
        wsProducer.sendSignalingMessage(dto.getTargetUserId(), Map.of(
                "type", dto.getType(),
                "callId", dto.getCallId(),
                "fromUserId", fromUserId,
                "data", dto.getData() != null ? dto.getData() : Map.of()
        ));
        log.debug("[语音] 信令转发: type={}, from={}, to={}", dto.getType(), fromUserId, dto.getTargetUserId());
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
            existing.setJoinTime(LocalDateTime.now());
            existing.setLeaveTime(null);
            existing.setConnectionStatus(1);
            participantMapper.updateById(existing);
        } else {
            VoiceCallParticipant p = new VoiceCallParticipant();
            p.setCallId(callId);
            p.setUserId(userId);
            p.setJoinTime(LocalDateTime.now());
            p.setConnectionStatus(1);
            participantMapper.insert(p);
        }
    }

    private void terminateCall(VoiceCall call) {
        if (call.getStatus() == CallStatus.IN_PROGRESS.getCode()) {
            call.setStatus(CallStatus.ENDED.getCode());
            call.setEndTime(LocalDateTime.now());
            if (call.getStartTime() != null) {
                long seconds = java.time.Duration.between(call.getStartTime(), call.getEndTime()).getSeconds();
                call.setDuration((int) seconds);
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
            throw new IllegalArgumentException("通话不存在: callId=" + callId);
        }
        return call;
    }

    private CallVO buildCallVO(VoiceCall call) {
        List<VoiceCallParticipant> participants = participantMapper.selectList(
                new LambdaQueryWrapper<VoiceCallParticipant>()
                        .eq(VoiceCallParticipant::getCallId, call.getCallId())
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
        vo.setParticipantCount(call.getParticipantCount());
        vo.setJoinable(call.getStatus() == CallStatus.IN_PROGRESS.getCode());
        vo.setParticipants(participantVOs);
        return vo;
    }
}
