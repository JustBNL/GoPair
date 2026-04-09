package com.gopair.messageservice.domain.dto;

import lombok.Data;

/**
 * 用户公开展示信息（与 message 服务 JOIN user 表字段对齐，供昵称/头像降级补全）
 */
@Data
public class UserPublicProfileDto {

    private Long userId;
    private String nickname;
    private String avatar;
}
