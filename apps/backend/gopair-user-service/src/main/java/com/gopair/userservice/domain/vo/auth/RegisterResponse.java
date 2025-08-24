package com.gopair.userservice.domain.vo.auth;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户注册响应
 * 
 * @author gopair
 */
@Data
public class RegisterResponse {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 用户邮箱
     */
    private String email;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 响应消息
     */
    private String message;
} 