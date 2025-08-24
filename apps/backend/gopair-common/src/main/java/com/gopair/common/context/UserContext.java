package com.gopair.common.context;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户上下文信息
 * 
 * 存储当前请求的用户相关信息，通过ThreadLocal在整个请求链路中传递
 * 
 * @author gopair
 */
@Data
@Builder
public class UserContext {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 请求时间
     */
    private LocalDateTime requestTime;

    /**
     * 请求ID，用于链路追踪
     */
    private String requestId;

    /**
     * 创建用户上下文对象
     * 
     * @param userId 用户ID
     * @param nickname 昵称
     * @return 用户上下文对象
     */
    public static UserContext of(Long userId, String nickname) {
        return UserContext.builder()
                .userId(userId)
                .nickname(nickname)
                .requestTime(LocalDateTime.now())
                .requestId(UUID.randomUUID().toString().replace("-", ""))
                .build();
    }

    /**
     * 检查用户上下文是否有效
     * 
     * @return 如果用户ID不为空则返回true
     */
    public boolean isValid() {
        return userId != null;
    }
} 