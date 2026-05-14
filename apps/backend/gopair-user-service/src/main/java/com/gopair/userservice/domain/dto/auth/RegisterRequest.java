package com.gopair.userservice.domain.dto.auth;

import com.gopair.common.constants.RegexConstants;
import com.gopair.common.constants.SystemConstants;
import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 用户注册请求
 *
 * @author gopair
 */
@Data
public class RegisterRequest {

    /**
     * 昵称
     */
    @NotBlank(message = SystemConstants.PARAM_MISSING)
    @Size(min = 1, max = 20, message = SystemConstants.NICKNAME_LENGTH_ERROR)
    private String nickname;

    /**
     * 用户邮箱
     */
    @NotBlank(message = SystemConstants.PARAM_MISSING)
    @Email(message = RegexConstants.EMAIL_FORMAT_ERROR)
    @Pattern(regexp = RegexConstants.EMAIL_PATTERN, message = RegexConstants.EMAIL_FORMAT_ERROR)
    private String email;

    /**
     * 密码
     */
    @NotBlank(message = SystemConstants.PARAM_MISSING)
    @Size(min = 6, max = 50, message = SystemConstants.PASSWORD_LENGTH_ERROR)
    private String password;

    /**
     * 邮箱验证码（测试环境@example.com可为空，绕过验证码校验）
     */
    private String code;
}
