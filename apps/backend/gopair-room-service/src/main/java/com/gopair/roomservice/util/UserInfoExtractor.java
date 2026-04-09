package com.gopair.roomservice.util;

import com.gopair.framework.context.UserContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

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

    @PostConstruct
    public void init() {
        log.info("[房间服务] 用户信息提取器初始化完成");
    }

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
        return UserContextHolder.hasContext() && UserContextHolder.getCurrentUserId() != null;
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