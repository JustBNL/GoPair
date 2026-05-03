package com.gopair.chatservice.domain.vo;

import com.gopair.chatservice.domain.dto.FriendStatusVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户搜索结果VO（用于好友搜索场景）。
 *
 * @author gopair
 */
@Data
@Schema(description = "用户搜索结果")
public class UserSearchResultVO {

    @Schema(description = "用户ID", example = "2")
    private Long userId;

    @Schema(description = "昵称", example = "张三")
    private String nickname;

    @Schema(description = "头像", example = "http://example.com/avatar.jpg")
    private String avatar;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "与当前用户的好友关系状态")
    private FriendStatusVO friendStatus;
}
