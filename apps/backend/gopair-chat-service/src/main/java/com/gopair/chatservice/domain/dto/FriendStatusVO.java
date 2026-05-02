package com.gopair.chatservice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 好友关系状态检查VO。
 *
 * @author gopair
 */
@Data
@Schema(description = "好友关系状态")
public class FriendStatusVO {

    @Schema(description = "是否为好友", example = "false")
    private Boolean isFriend;

    @Schema(description = "是否已发出申请", example = "false")
    private Boolean isRequestSent;

    @Schema(description = "是否收到申请", example = "false")
    private Boolean isRequestReceived;

    @Schema(description = "待处理的申请ID")
    private Long requestId;
}
