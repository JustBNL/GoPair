package com.gopair.adminservice.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 语音通话参与者实体，对应数据库voice_call_participant表
 */
@Data
@TableName("voice_call_participant")
public class VoiceCallParticipant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long callId;

    private Long userId;

    private LocalDateTime joinTime;

    private LocalDateTime leaveTime;

    private Integer connectionStatus;
}
