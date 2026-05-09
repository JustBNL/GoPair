package com.gopair.chatservice.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 批量查询会话详情VO，替代 N+1 查询场景。
 *
 * @author gopair
 */
@Data
@Schema(description = "会话详情（包含最新消息和消息数量）")
public class ConversationDetailVO {

    @Schema(description = "会话ID")
    private Long conversationId;

    @Schema(description = "好友用户ID")
    private Long friendId;

    @Schema(description = "好友昵称")
    private String friendNickname;

    @Schema(description = "好友头像")
    private String friendAvatar;

    @Schema(description = "最后消息内容预览")
    private String lastMessageContent;

    @Schema(description = "最后消息时间")
    private String lastMessageTime;

    @Schema(description = "最后消息类型：1-文本 2-图片 3-文件")
    private Integer lastMessageType;

    @Schema(description = "该会话消息总数")
    private Long messageCount;
}
