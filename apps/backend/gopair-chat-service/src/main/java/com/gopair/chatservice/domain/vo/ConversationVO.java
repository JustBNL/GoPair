package com.gopair.chatservice.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 私聊会话展示VO。
 *
 * @author gopair
 */
@Data
@Schema(description = "私聊会话信息")
public class ConversationVO {

    @Schema(description = "会话ID", example = "10000000002")
    private Long conversationId;

    @Schema(description = "好友用户ID", example = "2")
    private Long friendId;

    @Schema(description = "好友昵称", example = "张三")
    private String friendNickname;

    @Schema(description = "好友头像", example = "http://example.com/avatar.jpg")
    private String friendAvatar;

    @Schema(description = "最后消息内容预览", example = "好的，明天见！")
    private String lastMessageContent;

    @Schema(description = "最后消息时间")
    private String lastMessageTime;

    @Schema(description = "最后消息类型：1-文本 2-图片 3-文件", example = "1")
    private Integer lastMessageType;

    @Schema(description = "该会话消息总数", example = "10")
    private Long messageCount;
}
