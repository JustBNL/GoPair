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
} 