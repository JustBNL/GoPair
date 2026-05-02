package com.gopair.chatservice.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 好友展示VO。
 *
 * @author gopair
 */
@Data
@Schema(description = "好友信息")
public class FriendVO {

    @Schema(description = "好友用户ID", example = "2")
    private Long friendId;

    @Schema(description = "好友昵称", example = "张三")
    private String nickname;

    @Schema(description = "好友头像", example = "http://example.com/avatar.jpg")
    private String avatar;

    @Schema(description = "好友备注")
    private String remark;

    @Schema(description = "成为好友时间", example = "2024-12-20T15:30:00")
    private String createdAt;

    @Schema(description = "最后消息时间")
    private String lastMessageTime;

    @Schema(description = "最后消息内容预览")
    private String lastMessageContent;
}
