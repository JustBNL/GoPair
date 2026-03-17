package com.gopair.voiceservice.domain.dto;

import lombok.Data;

/**
 * WebRTC信令转发DTO
 *
 * @author gopair
 */
@Data
public class SignalingDto {

    /** 通话ID */
    private Long callId;

    /** 信令类型：offer / answer / ice-candidate */
    private String type;

    /** 目标用户ID */
    private Long targetUserId;

    /** 信令数据（SDP / ICE candidate 等） */
    private Object data;
}
