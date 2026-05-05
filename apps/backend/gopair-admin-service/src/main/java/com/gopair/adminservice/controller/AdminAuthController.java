package com.gopair.adminservice.controller;

import com.gopair.adminservice.service.AdminAuthService;
import com.gopair.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员认证控制器
 */
@Tag(name = "管理员认证")
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @Operation(summary = "管理员登录")
    @PostMapping("/login")
    public R<AdminAuthService.LoginResult> login(
            @RequestParam @NotBlank String username,
            @RequestParam @NotBlank String password) {
        return R.ok(adminAuthService.login(username, password));
    }
}
