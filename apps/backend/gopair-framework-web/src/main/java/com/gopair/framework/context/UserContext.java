package com.gopair.framework.context;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户上下文实体
 * 
 * 用于在请求处理过程中传递用户相关信息
 * 
 * @author gopair
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 用户昵称
     */
    private String nickname;
    
    /**
     * 创建用户上下文的便捷方法
     * 
     * @param userId 用户ID
     * @param nickname 用户昵称
     * @return 用户上下文实例
     */
    public static UserContext of(Long userId, String nickname) {
        return new UserContext(userId, nickname);
    }
} 