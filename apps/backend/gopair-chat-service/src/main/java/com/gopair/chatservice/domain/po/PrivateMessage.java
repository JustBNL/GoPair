package com.gopair.chatservice.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import com.gopair.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 私聊消息实体。
 *
 * @author gopair
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("private_message")
public class PrivateMessage extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID：min(senderId, receiverId) * 1_000_000_0000 + max(senderId, receiverId)。
     * 确保任意双方的会话 ID 唯一且对称。
     */
    private Long conversationId;

    /**
     * 发送者用户ID。
     */
    private Long senderId;

    /**
     * 接收者用户ID。
     */
    private Long receiverId;

    /**
     * 消息类型：1-文本 2-图片 3-文件
     */
    private Integer messageType;

    /**
     * 文本内容。
     */
    private String content;

    /**
     * 文件URL（图片/文件消息）。
     */
    private String fileUrl;

    /**
     * 文件名。
     */
    private String fileName;

    /**
     * 文件大小（字节）。
     */
    private Long fileSize;

    /**
     * 是否已撤回。
     */
    private Boolean isRecalled = false;

    /**
     * 撤回时间。
     */
    private LocalDateTime recalledAt;
}
