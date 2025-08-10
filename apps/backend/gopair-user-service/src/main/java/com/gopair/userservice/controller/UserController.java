package com.gopair.userservice.controller;

import com.gopair.common.core.PageResult;
import com.gopair.common.core.R;
import com.gopair.userservice.domain.dto.UserDto;
import com.gopair.userservice.domain.vo.UserVO;
import com.gopair.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

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
     * 创建用户
     */
    @Operation(summary = "创建用户", description = "创建新用户")
    @PostMapping
    public R<Boolean> createUser(@RequestBody UserDto userDto) {
        return R.ok(userService.createUser(userDto));
    }

    /**
     * 更新用户
     */
    @Operation(summary = "更新用户", description = "更新用户信息")
    @PutMapping
    public R<Boolean> updateUser(@RequestBody UserDto userDto) {
        return R.ok(userService.updateUser(userDto));
    }

    /**
     * 删除用户
     */
    @Operation(summary = "删除用户", description = "根据用户ID删除用户")
    @DeleteMapping("/{userId}")
    public R<Boolean> deleteUser(@PathVariable Long userId) {
        return R.ok(userService.deleteUser(userId));
    }

    /**
     * 根据ID查询用户
     */
    @Operation(summary = "查询用户", description = "根据用户ID查询用户信息")
    @GetMapping("/{userId}")
    public R<UserVO> getUserById(@PathVariable Long userId) {
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
} 