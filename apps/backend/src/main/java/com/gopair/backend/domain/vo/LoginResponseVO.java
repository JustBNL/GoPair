package com.gopair.backend.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应视图对象
 * 
 * 用于向前端返回登录结果信息
 * 
 * @author gopair
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应信息")
public class LoginResponseVO {

    /**
     * JWT令牌
     */
    @Schema(description = "JWT令牌")
    private String token;

    /**
     * 用户信息
     */
    @Schema(description = "用户信息")
    private UserInfoVO userInfo;
} 