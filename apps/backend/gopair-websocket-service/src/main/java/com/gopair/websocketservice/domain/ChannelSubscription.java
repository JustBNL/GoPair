package com.gopair.websocketservice.domain;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 频道订阅信息
 * 
 * @author gopair
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelSubscription {
    
    /**
     * 频道名称 (如: "room:chat:123", "user:456", "room:file:789")
     */
    private String channel;
    
    /**
     * 订阅的事件类型集合 (如: ["message", "typing", "join", "leave"])
     */
    private Set<String> eventTypes;
    
    /**
     * 订阅时间
     */
    private LocalDateTime subscribeTime;
    
    /**
     * 最后活跃时间 (用于智能清理)
     */
    private LocalDateTime lastActiveTime;
    
    /**
     * 订阅优先级 (1-10, 10为最高优先级)
     */
    private Integer priority;
    
    /**
     * 是否为自动订阅 (智能订阅系统创建的订阅)
     */
    private Boolean autoSubscribed;
    
    /**
     * 订阅来源 (如: "manual", "smart", "login")
     */
    private String source;
    
    /**
     * 更新最后活跃时间
     */
    public void updateLastActiveTime() {
        this.lastActiveTime = LocalDateTime.now();
    }
    
    /**
     * 检查是否为房间频道
     */
    public boolean isRoomChannel() {
        return channel != null && channel.startsWith("room:");
    }
    
    /**
     * 检查是否为用户频道
     */
    public boolean isUserChannel() {
        return channel != null && channel.startsWith("user:");
    }
    
    /**
     * 提取房间ID (仅适用于房间频道)
     */
    public Long extractRoomId() {
        if (!isRoomChannel()) {
            return null;
        }
        
        String[] parts = channel.split(":");
        if (parts.length >= 3) {
            try {
                return Long.valueOf(parts[2]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * 提取用户ID (仅适用于用户频道)
     */
    public Long extractUserId() {
        if (!isUserChannel()) {
            return null;
        }
        
        String[] parts = channel.split(":");
        if (parts.length >= 2) {
            try {
                return Long.valueOf(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
} 