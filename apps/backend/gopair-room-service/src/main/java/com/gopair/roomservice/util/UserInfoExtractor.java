package com.gopair.roomservice.util;

import com.gopair.common.context.UserContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户信息提取工具类
 * 
 * 提供用户信息获取和验证功能
 * 
 * @author gopair
 */
@Slf4j
@Component
public class UserInfoExtractor {

    /**
     * 获取当前用户ID
     * 
     * @return 用户ID，如果用户未登录则抛出异常
     * @throws IllegalStateException 用户未登录时抛出
     */
    public static Long getCurrentUserId() {
        Long userId = UserContextHolder.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("用户未登录");
        }
        return userId;
    }

    /**
     * 获取当前昵称
     * 
     * @return 昵称，如果用户未登录则返回默认值
     */
    public static String getCurrentNickname() {
        String nickname = UserContextHolder.getCurrentNickname();
        return nickname != null ? nickname : "未知用户";
    }

    /**
     * 验证用户是否已登录
     * 
     * @return 是否已登录
     */
    public static boolean isUserLoggedIn() {
        return UserContextHolder.hasValidContext() && UserContextHolder.getCurrentUserId() != null;
    }

    /**
     * 获取用户显示名称
     * 
     * @param userId 用户ID
     * @param customDisplayName 自定义显示名称
     * @return 显示名称
     */
    public static String getDisplayName(Long userId, String customDisplayName) {
        if (customDisplayName != null && !customDisplayName.trim().isEmpty()) {
            return customDisplayName.trim();
        }
        
        String nickname = getCurrentNickname();
        if (!"未知用户".equals(nickname)) {
            return nickname;
        }
        
        return "用户" + userId;
    }

    /**
     * 验证显示名称是否有效
     * 
     * @param displayName 显示名称
     * @return 是否有效
     */
    public static boolean isValidDisplayName(String displayName) {
        return displayName != null && 
               !displayName.trim().isEmpty() && 
               displayName.trim().length() <= 50;
    }

    /**
     * 格式化创建者信息
     * 
     * @param userId 用户ID
     * @return 创建者信息字符串
     */
    public static String formatCreator(Long userId) {
        String nickname = getCurrentNickname();
        if (!"未知用户".equals(nickname)) {
            return nickname + "(" + userId + ")";
        }
        return "用户" + userId;
    }
} 