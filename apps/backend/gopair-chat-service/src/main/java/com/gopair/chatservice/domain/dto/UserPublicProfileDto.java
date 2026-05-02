package com.gopair.chatservice.domain.dto;

import lombok.Data;

/**
 * 用户公开资料DTO（用于跨服务调用）。
 *
 * @author gopair
 */
@Data
public class UserPublicProfileDto {
    private Long userId;
    private String nickname;
    private String avatar;
}
