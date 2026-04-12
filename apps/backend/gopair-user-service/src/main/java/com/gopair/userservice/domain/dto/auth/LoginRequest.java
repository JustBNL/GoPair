package com.gopair.userservice.domain.dto.auth;

import com.gopair.common.constants.SystemConstants;
import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 用户登录请求
 *
 * @author gopair
 */
@Data
public class LoginRequest {

    /**
     * 用户邮箱
     */
    @NotBlank(message = SystemConstants.PARAM_MISSING)
    @Email(message = SystemConstants.EMAIL_FORMAT_ERROR)
    private String email;

    /**
     * 密码
     */
    @NotBlank(message = SystemConstants.PARAM_MISSING)
    private String password;
}
