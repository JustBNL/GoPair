package com.gopair.messageservice.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息展示VO
 * 
 * @author gopair
 */
@Data
@Schema(description = "消息信息")
public class MessageVO {

    /**
     * 消息ID
     */
    @Schema(description = "消息ID", example = "1")
    private Long messageId;

    /**
     * 房间ID
     */
    @Schema(description = "房间ID", example = "1")
    private Long roomId;

    /**
     * 发送者ID
     */
    @Schema(description = "发送者ID", example = "1")
    private Long senderId;

    /**
     * 发送者昵称
     */
    @Schema(description = "发送者昵称", example = "张三")
    private String senderNickname;

    /**
     * 发送者头像
     */
    @Schema(description = "发送者头像", example = "http://example.com/avatar.jpg")
    private String senderAvatar;

    /**
     * 消息类型：1-文本 2-图片 3-文件 4-语音
     */
    @Schema(description = "消息类型", example = "1")
    private Integer messageType;

    /**
     * 消息类型描述
     */
    @Schema(description = "消息类型描述", example = "文本消息")
    private String messageTypeDesc;

    /**
     * 消息内容
     */
    @Schema(description = "消息内容", example = "Hello, world!")
    private String content;

    /**
     * 文件URL
     */
    @Schema(description = "文件URL", example = "http://example.com/file.jpg")
    private String fileUrl;

    /**
     * 文件名
     */
    @Schema(description = "文件名", example = "image.jpg")
    private String fileName;

    /**
     * 文件大小
     */
    @Schema(description = "文件大小", example = "1024")
    private Long fileSize;

    /**
     * 回复的消息ID
     */
    @Schema(description = "回复的消息ID", example = "123")
    private Long replyToId;

    /**
     * 回复的消息内容（简略）
     */
    @Schema(description = "回复的消息内容", example = "原消息内容...")
    private String replyToContent;

    /**
     * 回复的消息发送者昵称
     */
    @Schema(description = "回复的消息发送者昵称", example = "李四")
    private String replyToSenderNickname;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2024-12-20T15:30:00")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间", example = "2024-12-20T15:30:00")
    private LocalDateTime updateTime;
} 