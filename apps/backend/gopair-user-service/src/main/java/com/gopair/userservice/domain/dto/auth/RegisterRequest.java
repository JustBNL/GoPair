package com.gopair.userservice.domain.dto.auth;

import com.gopair.common.constants.MessageConstants;
import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
    @NotBlank(message = MessageConstants.PARAM_MISSING)
    @Size(min = 1, max = 20, message = MessageConstants.NICKNAME_LENGTH_ERROR)
    private String nickname;

    /**
     * 用户邮箱
     */
    @NotBlank(message = MessageConstants.PARAM_MISSING)
    @Email(message = MessageConstants.EMAIL_FORMAT_ERROR)
    private String email;

    /**
     * 密码
     */
    @NotBlank(message = MessageConstants.PARAM_MISSING)
    @Size(min = 6, max = 50, message = MessageConstants.PASSWORD_LENGTH_ERROR)
    private String password;

    /**
     * 邮箱验证码
     */
    @NotBlank(message = MessageConstants.PARAM_MISSING)
    private String code;
} 