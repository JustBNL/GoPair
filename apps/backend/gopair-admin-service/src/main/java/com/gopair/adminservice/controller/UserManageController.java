package com.gopair.adminservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.adminservice.annotation.AdminAudit;
import com.gopair.adminservice.domain.po.User;
import com.gopair.adminservice.service.UserManageService;
import com.gopair.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户管理控制器
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserManageController {

    private final UserManageService userManageService;

    @Operation(summary = "分页查询用户")
    @GetMapping("/page")
    public R<Page<User>> getUserPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        UserManageService.UserPageQuery query = new UserManageService.UserPageQuery(pageNum, pageSize, keyword);
        return R.ok(userManageService.getUserPage(query));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{userId}")
    public R<Map<String, Object>> getUserDetail(@PathVariable Long userId) {
        try {
            return R.ok(userManageService.getUserDetail(userId));
        } catch (IllegalArgumentException e) {
            return R.fail(404, e.getMessage());
        }
    }

    @Operation(summary = "停用用户")
    @PostMapping("/{userId}/disable")
    @AdminAudit(operation = "USER_DISABLE", targetType = "USER")
    public R<Void> disableUser(@PathVariable Long userId) {
        try {
            userManageService.disableUser(userId);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        }
    }

    @Operation(summary = "启用用户")
    @PostMapping("/{userId}/enable")
    @AdminAudit(operation = "USER_ENABLE", targetType = "USER")
    public R<Void> enableUser(@PathVariable Long userId) {
        try {
            userManageService.enableUser(userId);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        }
    }
}
