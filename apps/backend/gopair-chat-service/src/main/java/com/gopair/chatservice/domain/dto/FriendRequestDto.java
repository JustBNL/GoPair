package com.gopair.chatservice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送好友请求DTO。
 *
 * @author gopair
 */
@Data
@Schema(description = "发送好友请求")
public class FriendRequestDto {

    @NotNull(message = "toUserId不能为空")
    @Schema(description = "目标用户ID", example = "2")
    private Long toUserId;

    @Size(max = 100, message = "附言不能超过100字符")
    @Schema(description = "申请附言")
    private String message;
}
