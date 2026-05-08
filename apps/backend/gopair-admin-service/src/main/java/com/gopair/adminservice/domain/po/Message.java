package com.gopair.adminservice.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gopair.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 消息实体类，对应数据库message表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message")
public class Message extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long messageId;

    private Long roomId;

    private Long senderId;

    private Integer messageType;

    private String content;

    private String fileUrl;

    private String fileName;

    private Long fileSize;

    private Long replyToId;

    private Boolean isRecalled = false;

    private LocalDateTime recalledAt;
}
