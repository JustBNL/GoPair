package com.gopair.websocketservice.domain.payload;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.util.Set;

/**
 * 订阅消息载荷对象
 * 提供类型安全的订阅消息数据结构
 * 
 * @author gopair
 */
@Data
@Builder
public class SubscriptionPayload {

    /**
     * 用户ID - 强类型Long，避免类型转换错误
     */
    @NonNull
    private Long userId;

    /**
     * 频道名称
     */
    @NonNull
    private String channel;

    /**
     * 要订阅的事件类型集合
     * 默认包含基本事件类型
     */
    @Builder.Default
    private Set<String> eventTypes = Set.of("default");

    /**
     * 订阅来源
     * 可选值：manual, auto, smart
     */
    @Builder.Default
    private String source = "manual";

    /**
     * 验证载荷数据是否有效
     * 
     * @return 验证是否通过
     */
    public boolean isValid() {
        return userId != null && userId > 0 && 
               channel != null && !channel.trim().isEmpty() &&
               eventTypes != null && !eventTypes.isEmpty();
    }

    /**
     * 获取用户ID的字符串表示（用于日志）
     * 
     * @return 用户ID字符串
     */
    public String getUserIdString() {
        return userId != null ? userId.toString() : "null";
    }

    /**
     * 检查是否包含特定事件类型
     * 
     * @param eventType 事件类型
     * @return 是否包含
     */
    public boolean containsEventType(String eventType) {
        return eventTypes != null && eventTypes.contains(eventType);
    }

    /**
     * 获取事件类型数量
     * 
     * @return 事件类型数量
     */
    public int getEventTypeCount() {
        return eventTypes != null ? eventTypes.size() : 0;
    }
} 