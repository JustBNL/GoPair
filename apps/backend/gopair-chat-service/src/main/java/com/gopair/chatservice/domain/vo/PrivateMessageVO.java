package com.gopair.chatservice.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 私聊消息展示VO。
 *
 * @author gopair
 */
@Data
@Schema(description = "私聊消息信息")
public class PrivateMessageVO {

    @Schema(description = "消息ID", example = "1")
    private Long messageId;

    @Schema(description = "会话ID", example = "10000000002")
    private Long conversationId;

    @Schema(description = "发送者ID", example = "1")
    private Long senderId;

    @Schema(description = "接收者ID", example = "2")
    private Long receiverId;

    @Schema(description = "发送者昵称", example = "张三")
    private String senderNickname;

    @Schema(description = "发送者头像", example = "http://example.com/avatar.jpg")
    private String senderAvatar;

    @Schema(description = "消息类型：1-文本 2-图片 3-文件", example = "1")
    private Integer messageType;

    @Schema(description = "消息类型描述", example = "文本消息")
    private String messageTypeDesc;

    @Schema(description = "消息内容", example = "你好！")
    private String content;

    @Schema(description = "文件URL", example = "http://example.com/file.jpg")
    private String fileUrl;

    @Schema(description = "文件名", example = "image.jpg")
    private String fileName;

    @Schema(description = "文件大小（字节）", example = "1024")
    private Long fileSize;

    @Schema(description = "是否已撤回", example = "false")
    private Boolean isRecalled;

    @Schema(description = "撤回时间")
    private LocalDateTime recalledAt;

    @Schema(description = "是否为当前用户发送", example = "true")
    private Boolean isOwn;

    @Schema(description = "创建时间", example = "2024-12-20T15:30:00")
    private LocalDateTime createTime;
}
