package com.gopair.messageservice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * 发送消息DTO
 * 
 * @author gopair
 */
@Data
@Schema(description = "发送消息请求")
public class SendMessageDto {

    /**
     * 房间ID
     */
    @NotNull(message = "房间ID不能为空")
    @Schema(description = "房间ID", example = "1")
    private Long roomId;

    /**
     * 消息类型：1-文本 2-图片 3-文件 4-语音 5-Emoji互动
     */
    @NotNull(message = "消息类型不能为空")
    @Min(value = 1, message = "消息类型值最小为1")
    @Max(value = 5, message = "消息类型值最大为5")
    @Schema(description = "消息类型", example = "1", allowableValues = {"1", "2", "3", "4", "5"})
    private Integer messageType;

    /**
     * 消息内容
     */
    @Schema(description = "消息内容", example = "Hello, world!")
    private String content;

    /**
     * 文件URL（当消息类型为文件时必填）
     */
    @Schema(description = "文件URL", example = "http://example.com/file.jpg")
    private String fileUrl;

    /**
     * 文件名（当消息类型为文件时必填）
     */
    @Schema(description = "文件名", example = "image.jpg")
    private String fileName;

    /**
     * 文件大小（当消息类型为文件时必填）
     */
    @Schema(description = "文件大小", example = "1024")
    private Long fileSize;

    /**
     * 回复的消息ID（可选）
     */
    @Schema(description = "回复的消息ID", example = "123")
    private Long replyToId;

    /**
     * 文件ID（当消息类型为文件时必填，由前端从 file-service 上传响应中传入，用于关联 room_file 记录）
     */
    @Schema(description = "文件ID（文件消息时由前端从上传响应中传入）", example = "1")
    private Long fileId;
} 