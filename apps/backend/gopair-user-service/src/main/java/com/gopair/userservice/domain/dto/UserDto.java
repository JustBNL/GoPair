package com.gopair.userservice.domain.dto;

import com.gopair.common.entity.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户DTO
 * 
 * @author gopair
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserDto extends BaseQuery {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户账号
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 用户邮箱
     */
    private String email;

    /**
     * 头像地址
     */
    private String avatar;

    /**
     * 帐号状态（0正常 1停用）
     */
    private Character status;

    /**
     * 备注
     */
    private String remark;
} 