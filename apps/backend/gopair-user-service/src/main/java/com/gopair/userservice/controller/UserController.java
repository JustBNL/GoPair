package com.gopair.userservice.controller;

import com.gopair.common.core.PageResult;
import com.gopair.common.core.R;
import com.gopair.userservice.domain.dto.UserDto;
import com.gopair.userservice.domain.dto.auth.ForgotPasswordRequest;
import com.gopair.userservice.domain.dto.auth.LoginRequest;
import com.gopair.userservice.domain.dto.auth.RegisterRequest;
import com.gopair.userservice.domain.dto.auth.SendCodeRequest;
import com.gopair.userservice.domain.vo.UserVO;
import com.gopair.userservice.domain.vo.auth.LoginResponse;
import com.gopair.userservice.domain.vo.auth.RegisterResponse;
import com.gopair.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户管理控制器
 * 
 * @author gopair
 */
@Tag(name = "用户管理", description = "用户相关接口")
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }
    
    /**
     * 发送邮箱验证码（注册 / 忘记密码通用）
     */
    @Operation(summary = "发送验证码", description = "向指定邮箱发送验证码，type=register(注册)/resetPassword(忘记密码)")
    @PostMapping("/sendCode")
    public R<Void> sendVerificationCode(
            @Parameter(description = "发送验证码请求", required = true)
            @Validated @RequestBody SendCodeRequest request) {
        userService.sendVerificationCode(request);
        return R.ok();
    }

    /**
     * 忘记密码（验证码重置）
     */
    @Operation(summary = "忘记密码", description = "通过邮箱验证码重置密码")
    @PostMapping("/forgotPassword")
    public R<Void> forgotPassword(
            @Parameter(description = "忘记密码请求", required = true)
            @Validated @RequestBody ForgotPasswordRequest request) {
        userService.forgotPassword(request);
        return R.ok();
    }

    /**
     * 用户登录（邮箱 + 密码）
     */
    @Operation(summary = "用户登录", description = "用户登录并返回令牌")
    @PostMapping("/login")
    public R<LoginResponse> login(
            @Parameter(description = "登录信息", required = true) 
            @Validated @RequestBody LoginRequest loginRequest) {
        return R.ok(userService.login(loginRequest));
    }

    /**
     * 用户注册（昵称 + 邮箱 + 密码）
     */
    @Operation(summary = "用户注册", description = "注册新用户")
    @PostMapping("/register")
    public R<RegisterResponse> register(
            @Parameter(description = "注册信息", required = true) 
            @Validated @RequestBody RegisterRequest registerRequest) {
        return R.ok(userService.register(registerRequest));
    }

    /**
     * 更新用户
     */
    @Operation(summary = "更新用户", description = "更新用户信息")
    @PutMapping
    public R<Boolean> updateUser(
            @Parameter(description = "用户信息", required = true) 
            @Validated @RequestBody UserDto userDto) {
        return R.ok(userService.updateUser(userDto));
    }

    /**
     * 删除用户
     */
    @Operation(summary = "删除用户", description = "根据用户ID删除用户")
    @DeleteMapping("/{userId}")
    public R<Boolean> deleteUser(
            @Parameter(description = "用户ID", required = true, example = "1") 
            @PathVariable Long userId) {
        return R.ok(userService.deleteUser(userId));
    }

    /**
     * 批量按 ID 查询用户（路径固定，须写在 /{userId} 之前，避免被当成路径变量）
     */
    @Operation(summary = "批量查询用户", description = "逗号分隔的用户 ID，用于房间成员列表等场景")
    @GetMapping("/by-ids")
    public R<List<UserVO>> listUsersByIds(
            @Parameter(description = "用户 ID，逗号分隔", example = "1,2,3")
            @RequestParam("ids") String ids) {
        if (!StringUtils.hasText(ids)) {
            return R.ok(List.of());
        }
        List<Long> idList = new ArrayList<>();
        for (String part : ids.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                idList.add(Long.parseLong(t));
            } catch (NumberFormatException ignored) {
                // 跳过非法片段
            }
        }
        return R.ok(userService.listUsersByIds(idList));
    }

    /**
     * 根据ID查询用户
     */
    @Operation(summary = "查询用户", description = "根据用户ID查询用户信息")
    @GetMapping("/{userId}")
    public R<UserVO> getUserById(
            @Parameter(description = "用户ID", required = true, example = "1") 
            @PathVariable Long userId) {
        return R.ok(userService.getUserById(userId));
    }

    /**
     * 分页查询用户列表
     */
    @Operation(summary = "分页查询用户", description = "分页查询用户列表")
    @GetMapping("/page")
    public R<PageResult<UserVO>> getUserPage(UserDto userDto) {
        return R.ok(userService.getUserPage(userDto));
    }

    /**
     * 注销账号
     */
    @Operation(summary = "注销账号", description = "注销当前账号，账号将被标记为已注销状态，原邮箱可重新注册")
    @DeleteMapping("/{userId}/cancel")
    public R<Void> cancelAccount(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long userId) {
        userService.cancelAccount(userId);
        return R.ok();
    }
} 