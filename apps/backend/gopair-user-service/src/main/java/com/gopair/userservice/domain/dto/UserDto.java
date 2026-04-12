package com.gopair.userservice.domain.dto;

import com.gopair.common.constants.SystemConstants;
import com.gopair.common.entity.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

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
     * 昵称
     */
    @Size(min = 1, max = 20, message = SystemConstants.NICKNAME_LENGTH_ERROR)
    private String nickname;

    /**
     * 密码
     */
    @Size(min = 6, max = 50, message = SystemConstants.PASSWORD_LENGTH_ERROR)
    private String password;

    /**
     * 用户邮箱
     */
    @Email(message = SystemConstants.EMAIL_FORMAT_ERROR)
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

    /**
     * 当前密码（修改密码时用于验证身份）
     */
    private String currentPassword;
}
