package com.gopair.chatservice.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 好友申请展示VO。
 *
 * @author gopair
 */
@Data
@Schema(description = "好友申请信息")
public class FriendRequestVO {

    @Schema(description = "申请记录ID", example = "1")
    private Long requestId;

    @Schema(description = "申请人用户ID", example = "1")
    private Long fromUserId;

    @Schema(description = "申请人昵称", example = "张三")
    private String fromNickname;

    @Schema(description = "申请人头像", example = "http://example.com/avatar.jpg")
    private String fromAvatar;

    @Schema(description = "被申请人用户ID", example = "2")
    private Long toUserId;

    @Schema(description = "申请附言")
    private String message;

    @Schema(description = "状态：pending/accepted/rejected", example = "pending")
    private String status;

    @Schema(description = "申请时间", example = "2024-12-20T15:30:00")
    private String createdAt;
}
