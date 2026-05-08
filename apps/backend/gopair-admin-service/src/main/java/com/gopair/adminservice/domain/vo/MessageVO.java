package com.gopair.adminservice.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 管理后台消息列表 VO，承载 JOIN 后的展示字段。
 */
@Data
public class MessageVO {

    // === 消息自身字段 ===
    private Long messageId;
    private Long roomId;
    private Long senderId;
    private Integer messageType;
    private String content;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private Long replyToId;
    private Boolean isRecalled;
    private LocalDateTime recalledAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // === JOIN 来的关联字段 ===
    /** 所属房间名称 */
    private String roomName;

    /** 房主用户ID（房间的 ownerId） */
    private Long ownerId;

    /** 发送者昵称 */
    private String senderNickname;

    /** 被回复消息的内容（引用回复场景） */
    private String replyToContent;

    /** 被回复消息的发送者昵称（引用回复场景） */
    private String replyToSenderNickname;
}
