package com.gopair.userservice.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息VO
 * 
 * @author gopair
 */
@Data
public class UserVO {

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
     * 头像地址
     */
    private String avatar;

    /**
     * 头像原图直链URL（用于下载原图）
     */
    private String avatarOriginalUrl;

    /**
     * 帐号状态（0正常 1停用）
     */
    private Character status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 访问令牌（登录时返回）
     */
    private String token;
} 