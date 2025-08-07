package com.gopair.backend.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户信息视图对象
 * 
 * 用于向前端展示安全的用户信息
 * 
 * @author gopair
 */
@Data
@Schema(description = "用户信息")
public class UserInfoVO {

    /**
     * 用户ID
     */
    @Schema(description = "用户ID")
    private Long userId;

    /**
     * 用户名
     */
    @Schema(description = "用户名")
    private String username;

    /**
     * 头像
     */
    @Schema(description = "头像URL")
    private String avatar;
} 