package com.gopair.userservice.domain.vo.auth;

import lombok.Data;

/**
 * 用户登录响应
 * 
 * @author gopair
 */
@Data
public class LoginResponse {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 访问令牌
     */
    private String token;

    /**
     * 用户邮箱
     */
    private String email;

    /**
     * 头像地址
     */
    private String avatar;

    /**
     * 头像原图直链URL（用于下载原图）
     */
    private String avatarOriginalUrl;
} 