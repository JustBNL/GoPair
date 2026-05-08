package com.gopair.adminservice.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 管理后台通话记录列表 VO，承载 JOIN 后的展示字段。
 */
@Data
public class VoiceCallVO {

    // === 通话自身字段 ===
    private Long callId;
    private Long roomId;
    private Long initiatorId;
    private Integer callType;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer duration;
    private Boolean isAutoCreated;

    // === JOIN 来的关联字段 ===
    /** 所属房间名称 */
    private String roomName;

    /** 发起人昵称 */
    private String initiatorNickname;

    /** 参与人数 */
    private Integer participantCount;

    /** 当前在线人数（connection_status = 1） */
    private Integer connectedCount;
}
