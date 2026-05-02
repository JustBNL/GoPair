package com.gopair.chatservice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送私聊消息请求DTO。
 *
 * @author gopair
 */
@Data
@Schema(description = "发送私聊消息请求")
public class SendPrivateMessageDto {

    @NotNull(message = "receiverId不能为空")
    @Schema(description = "接收者用户ID", example = "2")
    private Long receiverId;

    @NotNull(message = "messageType不能为空")
    @Schema(description = "消息类型：1-文本 2-图片 3-文件", example = "1")
    private Integer messageType;

    @Size(max = 2000, message = "消息内容不能超过2000字符")
    @Schema(description = "文本内容")
    private String content;

    @Schema(description = "文件URL")
    private String fileUrl;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;
}
