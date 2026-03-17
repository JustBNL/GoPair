package com.gopair.voiceservice.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通话信息VO
 *
 * @author gopair
 */
@Data
public class CallVO {

    private Long callId;

    private Long roomId;

    private Long initiatorId;

    /** 通话类型：1=一对一，2=多人 */
    private Integer callType;

    /** 状态：1=进行中，2=已结束，3=已取消 */
    private Integer status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer participantCount;

    /** 当前用户是否可以加入（status=IN_PROGRESS 时为 true） */
    private boolean joinable;

    private List<CallParticipantVO> participants;
}
