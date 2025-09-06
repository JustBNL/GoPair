package com.gopair.messageservice.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import com.gopair.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 消息实体类
 * 
 * 对应数据库message表
 * 
 * @author gopair
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message")
public class Message extends BaseEntity {

    /**
     * 消息ID
     */
    @TableId(type = IdType.AUTO)
    private Long messageId;

    /**
     * 房间ID
     */
    private Long roomId;

    /**
     * 发送者ID
     */
    private Long senderId;

    /**
     * 消息类型：1-文本 2-图片 3-文件 4-语音
     */
    private Integer messageType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 文件URL
     */
    private String fileUrl;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 回复的消息ID
     */
    private Long replyToId;
} 