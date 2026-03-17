package com.gopair.voiceservice.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通话参与者VO
 *
 * @author gopair
 */
@Data
public class CallParticipantVO {

    private Long userId;

    private LocalDateTime joinTime;

    private LocalDateTime leaveTime;

    /** 连接状态：1=已连接，2=已断开 */
    private Integer connectionStatus;

    private boolean initiator;
}
