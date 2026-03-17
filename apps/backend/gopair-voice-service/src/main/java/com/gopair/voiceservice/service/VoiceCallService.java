package com.gopair.voiceservice.service;

import com.gopair.voiceservice.domain.dto.SignalingDto;
import com.gopair.voiceservice.domain.vo.CallVO;

/**
 * 语音通话服务接口
 *
 * @author gopair
 */
public interface VoiceCallService {

    /** 自动创建房间通话（房间创建时调用） */
    CallVO createAutoCall(Long roomId, Long roomOwnerId);

    /** 加入通话（通过 callId，仅记录参与者，不广播 participant-join） */
    CallVO joinCall(Long callId, Long userId);

    /**
     * 加入或创建通话（按需创建模式）
     * 若房间无活跃通话，以当前用户为发起人创建；若已有活跃通话，直接加入。
     * 注意：此方法不广播 participant-join，需前端 WebRTC 就绪后调用 notifyReady。
     */
    CallVO joinOrCreateCall(Long roomId, Long userId);

    /**
     * 通知 WebRTC 就绪，向通话中其他参与者广播 participant-join。
     * 前端在本地音频流和 PeerConnection 建立完成后调用此接口。
     */
    void notifyReady(Long callId, Long userId);

    /** 离开通话 */
    void leaveCall(Long callId, Long userId);

    /** 结束通话（任意参与者可操作） */
    void endCall(Long callId, Long userId);

    /** 查询通话信息 */
    CallVO getCall(Long callId);

    /** 获取房间当前活跃通话（仅查询，不自动创建；无活跃通话时返回 null） */
    CallVO getActiveCall(Long roomId, Long userId);

    /** 转发 WebRTC 信令给目标用户 */
    void forwardSignaling(SignalingDto dto, Long fromUserId);
}
