package com.gopair.roomservice.domain.dto;

import lombok.Data;

/**
 * 用户公开展示信息（与 message 服务 JOIN user 表字段对齐，供成员列表等只读场景）
 */
@Data
public class UserPublicProfileDto {

    private Long userId;
    private String nickname;
    private String avatar;
}
