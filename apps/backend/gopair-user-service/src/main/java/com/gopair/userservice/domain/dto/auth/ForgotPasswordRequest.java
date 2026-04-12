package com.gopair.userservice.domain.dto.auth;

import com.gopair.common.constants.SystemConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 忘记密码请求
 *
 * @author gopair
 */
@Data
public class ForgotPasswordRequest {

    /**
     * 注册时使用的邮箱
     */
    @NotBlank(message = SystemConstants.PARAM_MISSING)
    @Email(message = SystemConstants.EMAIL_FORMAT_ERROR)
    private String email;

    /**
     * 邮箱验证码
     */
    @NotBlank(message = SystemConstants.PARAM_MISSING)
    private String code;

    /**
     * 新密码
     */
    @NotBlank(message = SystemConstants.PARAM_MISSING)
    @Size(min = 6, max = 50, message = SystemConstants.PASSWORD_LENGTH_ERROR)
    private String newPassword;
}
