package com.gopair.adminservice.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 语音通话实体，对应数据库voice_call表
 */
@Data
@TableName("voice_call")
public class VoiceCall {

    @TableId(value = "call_id", type = IdType.AUTO)
    private Long callId;

    private Long roomId;

    private Long initiatorId;

    private Integer callType;

    private Integer status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer duration;

    private Boolean isAutoCreated;
}
