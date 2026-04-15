package com.gopair.voiceservice.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 语音通话实体，对应 voice_call 表
 *
 * @author gopair
 */
@Data
@TableName("voice_call")
public class VoiceCall {

    @TableId(value = "call_id", type = IdType.AUTO)
    private Long callId;

    private Long roomId;

    private Long initiatorId;

    /** 通话类型：1=一对一，2=多人 */
    private Integer callType;

    /** 状态：1=进行中，2=已结束，3=已取消 */
    private Integer status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 通话时长（秒） */
    private Integer duration;

    /** 是否为自动创建（随房间自动创建） */
    private Boolean isAutoCreated;
}
