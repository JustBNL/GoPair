package com.gopair.voiceservice.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gopair.voiceservice.enums.ConnectionStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 语音通话参与者实体，对应 voice_call_participant 表
 *
 * @author gopair
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

    /** 连接状态，使用 {@link ConnectionStatus} 枚举管理 */
    private Integer connectionStatus;
}
