package com.gopair.userservice.domain.dto.auth;

import com.gopair.common.constants.RegexConstants;
import com.gopair.common.constants.SystemConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 发送验证码请求
 *
 * @author gopair
 */
@Data
public class SendCodeRequest {

    /**
     * 目标邮箱
     */
    @NotBlank(message = SystemConstants.PARAM_MISSING)
    @Email(message = RegexConstants.EMAIL_FORMAT_ERROR)
    @Pattern(regexp = RegexConstants.EMAIL_PATTERN, message = RegexConstants.EMAIL_FORMAT_ERROR)
    private String email;

    /**
     * 场景类型：register=注册，resetPassword=忘记密码
     */
    @NotBlank(message = SystemConstants.PARAM_MISSING)
    @Pattern(regexp = "^(register|resetPassword)$", message = "type参数非法")
    private String type;
}
