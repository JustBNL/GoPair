package com.gopair.websocketservice.service;

import com.gopair.websocketservice.domain.ChannelSubscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订阅管理服务
 * 负责管理WebSocket频道订阅关系
 * 
 * @author gopair
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionManagerService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisOperationService redisOperationService;

    /**
     * 用户订阅映射: userId → Set<ChannelSubscription>
     */
    private final Map<Long, Set<ChannelSubscription>> userSubscriptions = new ConcurrentHashMap<>();

    /**
     * 频道会话映射: channel → Set<sessionId>
     */
    private final Map<String, Set<String>> channelSessions = new ConcurrentHashMap<>();

    /**
     * 会话订阅映射: sessionId → Set<String> (channels)
     */
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();

    /**
     * 权限缓存: userId+roomId → 权限过期时间
     */
    private final Map<String, LocalDateTime> permissionCache = new ConcurrentHashMap<>();

    /**
     * 用户订阅频道
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param channel 频道名称
     * @param eventTypes 事件类型集合
     * @param source 订阅来源
     * @return 订阅是否成功
     */
    public boolean subscribeChannel(String sessionId, Long userId, String channel, 
                                  Set<String> eventTypes, String source) {
        try {
            // 1. 权限验证
            if (!validateChannelPermission(userId, channel)) {
                log.warn("[订阅管理] 用户权限验证失败: userId={}, channel={}", userId, channel);
                return false;
            }

            // 2. 创建订阅信息
            ChannelSubscription subscription = ChannelSubscription.builder()
                    .channel(channel)
                    .eventTypes(eventTypes)
                    .subscribeTime(LocalDateTime.now())
                    .lastActiveTime(LocalDateTime.now())
                    .priority(calculatePriority(channel, source))
                    .autoSubscribed("smart".equals(source))
                    .source(source)
                    .build();

            // 3. 更新本地映射
            userSubscriptions.computeIfAbsent(userId, k -> new HashSet<>()).add(subscription);
            channelSessions.computeIfAbsent(channel, k -> new CopyOnWriteArraySet<>()).add(sessionId);
            sessionSubscriptions.computeIfAbsent(sessionId, k -> new HashSet<>()).add(channel);

            // 4. 持久化到Redis
            Map<String, Object> subscriptionData = Map.of(
                    "channel", channel,
                    "eventTypes", eventTypes,
                    "source", source,
                    "subscribeTime", subscription.getSubscribeTime().toString(),
                    "priority", subscription.getPriority()
            );
            redisOperationService.saveUserSubscription(userId, channel, subscriptionData);

            log.info("[订阅管理] 用户订阅频道成功: userId={}, channel={}, eventTypes={}, source={}", 
                    userId, channel, eventTypes, source);
            return true;

        } catch (Exception e) {
            log.error("[订阅管理] 订阅频道失败: userId={}, channel={}", userId, channel, e);
            return false;
        }
    }

    /**
     * 用户取消订阅频道
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param channel 频道名称
     * @return 取消订阅是否成功
     */
    public boolean unsubscribeChannel(String sessionId, Long userId, String channel) {
        try {
            // 1. 移除本地映射
            Set<ChannelSubscription> userSubs = userSubscriptions.get(userId);
            if (userSubs != null) {
                userSubs.removeIf(sub -> channel.equals(sub.getChannel()));
                if (userSubs.isEmpty()) {
                    userSubscriptions.remove(userId);
                }
            }

            Set<String> channelSessions = this.channelSessions.get(channel);
            if (channelSessions != null) {
                channelSessions.remove(sessionId);
                if (channelSessions.isEmpty()) {
                    this.channelSessions.remove(channel);
                }
            }

            Set<String> sessionSubs = sessionSubscriptions.get(sessionId);
            if (sessionSubs != null) {
                sessionSubs.remove(channel);
                if (sessionSubs.isEmpty()) {
                    sessionSubscriptions.remove(sessionId);
                }
            }

            // 2. Redis清理
            redisOperationService.removeUserSubscription(userId, channel);

            log.info("[订阅管理] 用户取消订阅频道: userId={}, channel={}", userId, channel);
            return true;

        } catch (Exception e) {
            log.error("[订阅管理] 取消订阅频道失败: userId={}, channel={}", userId, channel, e);
            return false;
        }
    }

    /**
     * 获取频道的所有订阅会话
     * 
     * @param channel 频道名称
     * @param eventType 事件类型 (可选，用于精确过滤)
     * @return 会话ID集合
     */
    public Set<String> getChannelSubscribers(String channel, String eventType) {
        Set<String> sessions = channelSessions.get(channel);
        if (sessions == null) {
            return Collections.emptySet();
        }

        // 如果指定了事件类型，需要进一步过滤
        if (eventType != null) {
            return sessions.stream()
                    .filter(sessionId -> isSessionSubscribedToEvent(sessionId, channel, eventType))
                    .collect(Collectors.toSet());
        }

        return new HashSet<>(sessions);
    }

    /**
     * 获取用户的所有订阅
     * 
     * @param userId 用户ID
     * @return 订阅集合
     */
    public Set<ChannelSubscription> getUserSubscriptions(Long userId) {
        return new HashSet<>(userSubscriptions.getOrDefault(userId, Collections.emptySet()));
    }

    /**
     * 清理会话的所有订阅
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     */
    public void cleanupSessionSubscriptions(String sessionId, Long userId) {
        try {
            // 1. 获取会话的所有订阅
            Set<String> channels = sessionSubscriptions.remove(sessionId);
            if (channels == null) {
                return;
            }

            // 2. 从频道映射中移除会话
            for (String channel : channels) {
                Set<String> channelSessions = this.channelSessions.get(channel);
                if (channelSessions != null) {
                    channelSessions.remove(sessionId);
                    if (channelSessions.isEmpty()) {
                        this.channelSessions.remove(channel);
                    }
                }
            }

            // 3. 清理用户订阅（保留其他会话的订阅）
            if (userId != null) {
                cleanupUserSubscriptions(userId, channels);
            }

            log.info("[订阅管理] 清理会话订阅: sessionId={}, channels={}", sessionId, channels);

        } catch (Exception e) {
            log.error("[订阅管理] 清理会话订阅失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 验证频道订阅权限
     * 整合自PermissionValidationService的权限验证逻辑
     * 
     * @param userId 用户ID
     * @param channel 频道名称
     * @return 是否有权限
     */
    private boolean validateChannelPermission(Long userId, String channel) {
        try {
            log.debug("[订阅管理] 开始权限验证: userId={}, channel={}", userId, channel);

        // 用户频道：只能订阅自己的频道
        if (channel.startsWith("user:")) {
            String[] parts = channel.split(":");
            if (parts.length >= 2) {
                try {
                    Long channelUserId = Long.valueOf(parts[1]);
                        boolean hasPermission = userId.equals(channelUserId);
                        log.debug("[订阅管理] 用户频道权限验证: userId={}, channelUserId={}, allowed={}", 
                                userId, channelUserId, hasPermission);
                        return hasPermission;
                } catch (NumberFormatException e) {
                        log.warn("[订阅管理] 用户频道ID格式错误: channel={}", channel);
                    return false;
                }
            }
            return false;
        }

        // 房间频道：需要验证房间成员权限
        if (channel.startsWith("room:")) {
            String[] parts = channel.split(":");
            Long roomId = null;
            
            try {
                if (parts.length == 2) {
                    // 2段式格式: room:roomId
                    roomId = Long.valueOf(parts[1]);
                    log.debug("[订阅管理] 解析2段式房间频道: channel={}, roomId={}", channel, roomId);
                } else if (parts.length >= 3) {
                    // 3段式格式: room:type:roomId
                    roomId = Long.valueOf(parts[2]);
                    log.debug("[订阅管理] 解析3段式房间频道: channel={}, roomId={}", channel, roomId);
                } else {
                    log.warn("[订阅管理] 房间频道格式不正确: channel={}, parts={}", channel, parts.length);
                    return false;
                }
                
                boolean hasPermission = validateRoomPermission(userId, roomId);
                log.debug("[订阅管理] 房间频道权限验证: userId={}, roomId={}, allowed={}", 
                        userId, roomId, hasPermission);
                return hasPermission;
                
            } catch (NumberFormatException e) {
                log.warn("[订阅管理] 房间频道ID格式错误: channel={}, error={}", channel, e.getMessage());
                return false;
            }
        }

        // 系统频道：所有用户都可以订阅
        if (channel.startsWith("system:")) {
                log.debug("[订阅管理] 系统频道权限验证: userId={}, channel={}, allowed=true", userId, channel);
            return true;
        }

        // 其他频道：默认拒绝
            log.warn("[订阅管理] 未知频道类型权限验证: userId={}, channel={}, allowed=false", userId, channel);
            return false;

        } catch (Exception e) {
            log.error("[订阅管理] 权限验证异常: userId={}, channel={}", userId, channel, e);
        return false;
        }
    }

    /**
     * 验证房间权限 (带缓存)
     * 简化版本，暂时允许所有房间访问
     */
    private boolean validateRoomPermission(Long userId, Long roomId) {
        try {
        String cacheKey = userId + ":" + roomId;
        LocalDateTime cacheExpire = permissionCache.get(cacheKey);
        
        // 缓存有效
        if (cacheExpire != null && cacheExpire.isAfter(LocalDateTime.now())) {
                log.debug("[订阅管理] 房间权限缓存命中: userId={}, roomId={}", userId, roomId);
            return true;
        }

            // 简化版：暂时允许所有房间访问，后续可接入房间服务
            boolean hasPermission = true;
        
        if (hasPermission) {
            // 缓存权限结果5分钟
            permissionCache.put(cacheKey, LocalDateTime.now().plusMinutes(5));
                log.debug("[订阅管理] 房间权限验证并缓存: userId={}, roomId={}, allowed=true（简化版）", userId, roomId);
        }
        
        return hasPermission;

        } catch (Exception e) {
            log.error("[订阅管理] 房间权限验证异常: userId={}, roomId={}", userId, roomId, e);
            return false;
        }
    }

    /**
     * 计算订阅优先级
     */
    private Integer calculatePriority(String channel, String source) {
        // 用户频道：最高优先级
        if (channel.startsWith("user:")) {
            return 10;
        }
        
        // 房间聊天：高优先级
        if (channel.contains(":chat:")) {
            return 8;
        }
        
        // 房间语音：高优先级
        if (channel.contains(":voice:")) {
            return 8;
        }
        
        // 房间文件：中等优先级
        if (channel.contains(":file:")) {
            return 6;
        }
        
        // 房间系统：中等优先级
        if (channel.contains(":system:")) {
            return 6;
        }
        
        // 手动订阅：提升优先级
        if ("manual".equals(source)) {
            return 9;
        }
        
        // 默认优先级
        return 5;
    }

    /**
     * 检查会话是否订阅了特定事件
     */
    private boolean isSessionSubscribedToEvent(String sessionId, String channel, String eventType) {
        // 先检查会话是否订阅了该频道
        Set<String> sessionChannels = sessionSubscriptions.get(sessionId);
        if (sessionChannels == null || !sessionChannels.contains(channel)) {
            return false;
        }
        
        // 获取频道的会话映射
        Set<String> channelSessions = this.channelSessions.get(channel);
        if (channelSessions == null || !channelSessions.contains(sessionId)) {
            return false;
        }
        
        // 通过sessionId查找对应的用户订阅
        for (Map.Entry<Long, Set<ChannelSubscription>> entry : userSubscriptions.entrySet()) {
            for (ChannelSubscription sub : entry.getValue()) {
                if (channel.equals(sub.getChannel())) {
                    // 检查事件类型匹配
                    if (sub.getEventTypes() == null || sub.getEventTypes().isEmpty()) {
                        // 如果没有指定事件类型，允许所有事件
                        return true;
                    }
                    // 检查是否包含目标事件类型
                    if (sub.getEventTypes().contains(eventType)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    /**
     * 恢复用户订阅状态（连接建立时调用）
     * 
     * @param userId 用户ID
     * @return 恢复的订阅数量
     */
    public int restoreUserSubscriptionState(Long userId) {
        try {
            // 从Redis恢复用户订阅状态
            Map<Object, Object> persistedData = redisOperationService.getUserSubscriptions(userId);
            
            if (persistedData.isEmpty()) {
                log.debug("[订阅管理] 用户无持久化订阅数据: userId={}", userId);
                return 0;
            }
            
            Set<ChannelSubscription> persistedSubscriptions = new HashSet<>();
            // 转换Redis数据为ChannelSubscription对象
            for (Map.Entry<Object, Object> entry : persistedData.entrySet()) {
                try {
                    String channel = (String) entry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> subData = (Map<String, Object>) entry.getValue();
                    
                    // 构建ChannelSubscription对象
                    ChannelSubscription subscription = ChannelSubscription.builder()
                            .channel(channel)
                            .eventTypes((Set<String>) subData.get("eventTypes"))
                            .subscribeTime(LocalDateTime.parse((String) subData.get("subscribeTime")))
                            .priority((Integer) subData.get("priority"))
                            .autoSubscribed("auto".equals(subData.get("source")))
                            .build();
                    
                    persistedSubscriptions.add(subscription);
                    log.debug("[订阅管理] 恢复订阅: userId={}, channel={}", userId, channel);
                    
                } catch (Exception e) {
                    log.warn("[订阅管理] 恢复单个订阅失败: userId={}, error={}", userId, e.getMessage());
                }
            }

            // 恢复到内存映射
            userSubscriptions.put(userId, persistedSubscriptions);
            
            log.info("[订阅管理] 恢复用户订阅状态: userId={}, count={}", userId, persistedSubscriptions.size());
            return persistedSubscriptions.size();
            
        } catch (Exception e) {
            log.error("[订阅管理] 恢复用户订阅状态失败: userId={}", userId, e);
            return 0;
        }
    }

    /**
     * 批量持久化所有活跃用户的订阅状态
     */
    public void batchPersistAllSubscriptions() {
        try {
            if (!userSubscriptions.isEmpty()) {
                // 批量持久化所有用户订阅到Redis
                for (Map.Entry<Long, Set<ChannelSubscription>> entry : userSubscriptions.entrySet()) {
                    Long userId = entry.getKey();
                    Set<ChannelSubscription> subscriptions = entry.getValue();
                    
                    Map<String, Object> subscriptionData = new HashMap<>();
                    for (ChannelSubscription subscription : subscriptions) {
                        subscriptionData.put(subscription.getChannel(), Map.of(
                                "channel", subscription.getChannel(),
                                "eventTypes", subscription.getEventTypes(),
                                                                 "source", Boolean.TRUE.equals(subscription.getAutoSubscribed()) ? "auto" : "manual",
                                "subscribeTime", subscription.getSubscribeTime().toString(),
                                "priority", subscription.getPriority()
                        ));
                    }
                    
                    redisOperationService.batchSaveUserSubscriptions(userId, subscriptionData);
                }
                log.info("[订阅管理] 批量持久化订阅状态完成: users={}", userSubscriptions.size());
            }
        } catch (Exception e) {
            log.error("[订阅管理] 批量持久化订阅状态失败", e);
        }
    }

    /**
     * 清理用户订阅（保留其他会话的订阅）
     */
    private void cleanupUserSubscriptions(Long userId, Set<String> channels) {
        Set<ChannelSubscription> userSubs = userSubscriptions.get(userId);
        if (userSubs == null) {
            return;
        }

        // 移除指定频道的订阅
        userSubs.removeIf(sub -> channels.contains(sub.getChannel()));
        
        if (userSubs.isEmpty()) {
            userSubscriptions.remove(userId);
        }
    }

    /**
     * 获取订阅统计信息
     */
    public Map<String, Object> getSubscriptionStats() {
        int totalUsers = userSubscriptions.size();
        int totalChannels = channelSessions.size();
        int totalSubscriptions = userSubscriptions.values().stream()
                .mapToInt(Set::size)
                .sum();

        return Map.of(
                "totalUsers", totalUsers,
                "totalChannels", totalChannels,
                "totalSubscriptions", totalSubscriptions,
                "avgSubscriptionsPerUser", totalUsers > 0 ? (double) totalSubscriptions / totalUsers : 0
        );
    }

    /**
     * 清理过期的权限缓存 (定时任务)
     * 迁移自ScheduledTaskService
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 3600000) // 1小时
    public void cleanupExpiredPermissions() {
        try {
        LocalDateTime now = LocalDateTime.now();
            int sizeBefore = permissionCache.size();
            
        permissionCache.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
            
            int cleaned = sizeBefore - permissionCache.size();
            if (cleaned > 0) {
                log.info("[订阅管理] 清理过期权限缓存: {} 项", cleaned);
            } else {
                log.debug("[订阅管理] 权限缓存清理完成，无过期项");
            }
        } catch (Exception e) {
            log.error("[订阅管理] 清理过期权限缓存失败", e);
        }
    }

    /**
     * 每日深度订阅清理任务
     * 迁移自ScheduledTaskService
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点
    public void dailySubscriptionCleanupTask() {
        log.info("[订阅管理] 开始执行每日订阅清理任务...");
        
        try {
            // 清理长期未使用的订阅状态
            cleanupLongTermUnusedSubscriptions();
            
            log.info("[订阅管理] 每日订阅清理任务完成");
        } catch (Exception e) {
            log.error("[订阅管理] 每日订阅清理任务失败", e);
        }
    }

    /**
     * 清理长期未使用的订阅状态
     * 迁移自ScheduledTaskService
     */
    private void cleanupLongTermUnusedSubscriptions() {
        try {
            // 简化版实现：清理内存中的过期订阅数据
            int cleanedCount = 0;
            
            // 清理权限缓存中的过期项
            var permissionIterator = permissionCache.entrySet().iterator();
            while (permissionIterator.hasNext()) {
                var entry = permissionIterator.next();
                if (entry.getValue().isBefore(LocalDateTime.now().minusDays(7))) {
                    permissionIterator.remove();
                    cleanedCount++;
                }
            }
            
            if (cleanedCount > 0) {
                log.info("[订阅管理] 清理长期未使用数据: {} 项", cleanedCount);
            }
            
            log.debug("[订阅管理] 长期未使用订阅清理完成");
        } catch (Exception e) {
            log.error("[订阅管理] 清理长期未使用订阅失败", e);
        }
    }

    /**
     * 获取用户最近活跃的订阅
     * 
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 最近活跃的订阅列表
     */
    public List<ChannelSubscription> getRecentActiveSubscriptions(Long userId, int limit) {
        Set<ChannelSubscription> subscriptions = userSubscriptions.get(userId);
        if (subscriptions == null) {
            return Collections.emptyList();
        }

        return subscriptions.stream()
                .sorted(Comparator.comparing(ChannelSubscription::getLastActiveTime).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 更新订阅活跃时间
     * 
     * @param userId 用户ID
     * @param channel 频道名称
     */
    public void updateSubscriptionActivity(Long userId, String channel) {
        Set<ChannelSubscription> subscriptions = userSubscriptions.get(userId);
        if (subscriptions != null) {
            subscriptions.stream()
                    .filter(sub -> channel.equals(sub.getChannel()))
                    .forEach(ChannelSubscription::updateLastActiveTime);
        }
    }
} 