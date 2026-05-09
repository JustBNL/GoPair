package com.gopair.websocketservice.service;

import com.gopair.websocketservice.constants.WebSocketConstants;
import com.gopair.websocketservice.domain.ChannelSubscription;
import com.gopair.websocketservice.domain.SubscriptionData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionManagerService {

    private final SubscriptionStore subscriptionStore;

    /**
     * 本地订阅与权限缓存，仅作为 Redis 真相的短期镜像，避免频繁远程访问。
     * 长期订阅状态与权限结果以 Redis 为权威来源。
     */
    private final Map<Long, Set<ChannelSubscription>> userSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> channelSessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> permissionCache = new ConcurrentHashMap<>();
    /** sessionId → userId 映射，用于在路由时从 sessionId 反查 userId，精确匹配订阅的 eventTypes */
    private final Map<String, Long> sessionUserMap = new ConcurrentHashMap<>();

    public boolean subscribeChannel(String sessionId, Long userId, String channel, 
                                  Set<String> eventTypes, String source) {
        try {
            if (!validateChannelPermission(userId, channel)) {
                log.warn("[订阅管理] 用户权限验证失败: userId={}, channel={}", userId, channel);
                return false;
            }

            ChannelSubscription subscription = ChannelSubscription.builder()
                    .channel(channel)
                    .eventTypes(eventTypes)
                    .subscribeTime(LocalDateTime.now())
                    .lastActiveTime(LocalDateTime.now())
                    .priority(calculatePriority(channel, source))
                    .autoSubscribed("smart".equals(source))
                    .source(source)
                    .build();

            userSubscriptions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(subscription);
            channelSessions.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
            sessionSubscriptions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(channel);
            sessionUserMap.put(sessionId, userId);

            SubscriptionData subscriptionData = SubscriptionData.builder()
                    .eventTypes(eventTypes)
                    .source(source)
                    .subscribeTime(subscription.getSubscribeTime())
                    .priority(subscription.getPriority())
                    .build();
            subscriptionStore.saveUserSubscription(userId, channel, subscriptionData);

            log.info("[订阅管理] 用户订阅频道成功: userId={}, channel={}, eventTypes={}, source={}", 
                    userId, channel, eventTypes, source);
            return true;

        } catch (Exception e) {
            log.error("[订阅管理] 订阅频道失败: userId={}, channel={}", userId, channel, e);
            return false;
        }
    }

    public boolean unsubscribeChannel(String sessionId, Long userId, String channel) {
        try {
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
                    sessionUserMap.remove(sessionId);
                }
            }

            subscriptionStore.removeUserSubscription(userId, channel);

            log.info("[订阅管理] 用户取消订阅频道: userId={}, channel={}", userId, channel);
            return true;

        } catch (Exception e) {
            log.error("[订阅管理] 取消订阅频道失败: userId={}, channel={}", userId, channel, e);
            return false;
        }
    }

    public Set<String> getChannelSubscribers(String channel, String eventType) {
        Set<String> sessions = channelSessions.get(channel);
        if (sessions == null) {
            log.warn("[订阅管理] 频道在 channelSessions 中无记录: channel={}", channel);
            return Collections.emptySet();
        }

        log.info("[订阅管理] 查询频道订阅者: channel={}, eventType={}, sessionsInChannel={}", channel, eventType, sessions);
        if (eventType == null) return new HashSet<>(sessions);

        Set<String> filtered = sessions.stream()
                .filter(sessionId -> isSessionSubscribedToEvent(sessionId, channel, eventType))
                .collect(Collectors.toSet());
        log.info("[订阅管理] 过滤后订阅者: channel={}, eventType={}, filteredCount={}, filteredSessions={}", channel, eventType, filtered.size(), filtered);
        return filtered;
    }

    public Set<ChannelSubscription> getUserSubscriptions(Long userId) {
        return new HashSet<>(userSubscriptions.getOrDefault(userId, Collections.emptySet()));
    }

    public void cleanupSessionSubscriptions(String sessionId, Long userId) {
        try {
            Set<String> channels = sessionSubscriptions.remove(sessionId);
            sessionUserMap.remove(sessionId);
            if (channels == null) return;

            for (String channel : channels) {
                Set<String> channelSessions = this.channelSessions.get(channel);
                if (channelSessions != null) {
                    channelSessions.remove(sessionId);
                    if (channelSessions.isEmpty()) {
                        this.channelSessions.remove(channel);
                    }
                }
            }

            if (userId != null) {
                cleanupUserSubscriptions(userId, channels);
            }

            log.info("[订阅管理] 清理会话订阅: sessionId={}, channels={}", sessionId, channels);
        } catch (Exception e) {
            log.error("[订阅管理] 清理会话订阅失败: sessionId={}", sessionId, e);
        }
    }

    private boolean validateChannelPermission(Long userId, String channel) {
        try {
            if (channel.startsWith(WebSocketConstants.CHANNEL_PREFIX_USER)) {
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

            if (channel.startsWith(WebSocketConstants.CHANNEL_PREFIX_ROOM)) {
                String[] parts = channel.split(":");
                Long roomId = null;
                
                try {
                    if (parts.length == 2) {
                        roomId = Long.valueOf(parts[1]);
                    } else if (parts.length >= 3) {
                        roomId = Long.valueOf(parts[2]);
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

            if (channel.startsWith(WebSocketConstants.CHANNEL_PREFIX_SYSTEM)) {
                log.debug("[订阅管理] 系统频道权限验证: userId={}, channel={}, allowed=true", userId, channel);
                return true;
            }

            log.warn("[订阅管理] 未知频道类型权限验证: userId={}, channel={}, allowed=false", userId, channel);
            return false;

        } catch (Exception e) {
            log.error("[订阅管理] 权限验证异常: userId={}, channel={}", userId, channel, e);
            return false;
        }
    }

    private boolean validateRoomPermission(Long userId, Long roomId) {
        try {
            String cacheKey = userId + ":" + roomId;
            LocalDateTime cacheExpire = permissionCache.get(cacheKey);

            if (cacheExpire != null && cacheExpire.isAfter(LocalDateTime.now())) {
                log.debug("[订阅管理] 房间权限本地缓存命中: userId={}, roomId={}", userId, roomId);
                return true;
            }

            // 先尝试从 Redis 权限缓存获取结果
            String resourceKey = userId + ":" + roomId;
            Optional<Boolean> cachedPermission = subscriptionStore.getCachedUserPermission(userId, resourceKey);
            if (cachedPermission.isPresent()) {
                boolean allowed = Boolean.TRUE.equals(cachedPermission.get());
                if (allowed) {
                    permissionCache.put(cacheKey, LocalDateTime.now().plusMinutes(5));
                }
                log.debug("[订阅管理] 房间权限Redis缓存命中: userId={}, roomId={}, allowed={}", userId, roomId, allowed);
                return allowed;
            }

            // TODO: 这里应接入真实的房间权限校验逻辑，目前保持简化版始终允许的行为以避免改变外部语义
            boolean hasPermission = true;
            if (hasPermission) {
                permissionCache.put(cacheKey, LocalDateTime.now().plusMinutes(5));
                subscriptionStore.cacheUserPermission(userId, resourceKey, true);
                log.debug("[订阅管理] 房间权限验证并写入缓存: userId={}, roomId={}, allowed=true（简化版）", userId, roomId);
            }

            return hasPermission;

        } catch (Exception e) {
            log.error("[订阅管理] 房间权限验证异常: userId={}, roomId={}", userId, roomId, e);
            return false;
        }
    }

    private Integer calculatePriority(String channel, String source) {
        if (channel.startsWith(WebSocketConstants.CHANNEL_PREFIX_USER)) return 10;
        if (channel.contains(":chat:")) return 8;
        if (channel.contains(":voice:")) return 8;
        if (channel.contains(":file:")) return 6;
        if (channel.contains(":system:")) return 6;
        if ("manual".equals(source)) return 9;
        return 5;
    }

    private boolean isSessionSubscribedToEvent(String sessionId, String channel, String eventType) {
        Set<String> sessionChannels = sessionSubscriptions.get(sessionId);
        if (sessionChannels == null || !sessionChannels.contains(channel)) {
            log.debug("[订阅管理] sessionChannels 校验失败: sessionId={}, channel={}, sessionChannels={}", sessionId, channel, sessionChannels);
            return false;
        }

        Set<String> channelSessions = this.channelSessions.get(channel);
        if (channelSessions == null || !channelSessions.contains(sessionId)) {
            log.debug("[订阅管理] channelSessions 校验失败: sessionId={}, channel={}, channelSessions={}", sessionId, channel, channelSessions);
            return false;
        }

        // 通过 sessionUserMap 精确反查该 session 对应的 userId，避免误用其他用户的订阅配置
        Long userId = sessionUserMap.get(sessionId);
        log.debug("[订阅管理] sessionId={} -> userId={}", sessionId, userId);
        if (userId == null) {
            // sessionUserMap 中无记录时降级：允许投递，避免因内存状态不一致漏发消息
            log.warn("[订阅管理] sessionUserMap 中无 userId，降级为允许投递: sessionId={}, channel={}", sessionId, channel);
            return true;
        }

        Set<ChannelSubscription> subscriptions = userSubscriptions.get(userId);
        log.debug("[订阅管理] userId={} 的订阅列表: {}", userId, subscriptions);
        if (subscriptions == null) return true;

        for (ChannelSubscription sub : subscriptions) {
            if (channel.equals(sub.getChannel())) {
                Set<String> eventTypes = sub.getEventTypes();
                log.debug("[订阅管理] 匹配到频道订阅: sessionId={}, userId={}, channel={}, eventTypes={}, 查询eventType={}", sessionId, userId, channel, eventTypes, eventType);
                // eventTypes 为空表示订阅全部事件类型
                if (eventTypes == null || eventTypes.isEmpty()) return true;
                return eventTypes.contains(eventType);
            }
        }
        log.debug("[订阅管理] 未在 userId={} 的订阅列表中找到 channel={}", userId, channel);
        return false;
    }

    /**
     * 从 Redis 恢复用户在失联期间的订阅状态，将持久化数据合并到本地内存索引中。
     *
     * * [核心策略]
     * - 合并而非覆盖：保留本端已写入内存的订阅（来自 performLoginBasicSubscription），将 Redis 中的订阅合并进去。
     * - 全量索引重建：恢复时同时重建 channelSessions / sessionSubscriptions / sessionUserMap 三个反向索引，保证消息路由能命中。
     *
     * * [执行链路]
     * 1. 从 Redis 读取 ws:user-subscriptions:{userId} 的全部订阅数据。
     * 2. 解析并重建每个订阅的 ChannelSubscription 对象（不含 source 字段，从 autoSubscribed 推断）。
     * 3. 合并到 userSubscriptions（addAll）。
     * 4. 重建 channelSessions[channel] ← sessionId。
     * 5. 重建 sessionSubscriptions[sessionId] ← channel。
     * 6. 重建 sessionUserMap[sessionId] ← userId。
     *
     * @param userId    用户 ID
     * @param sessionId 当前 sessionId（用于重建反向索引）
     * @return 恢复的订阅数量
     */
    public int restoreUserSubscriptionState(Long userId, String sessionId) {
        try {
            Map<String, SubscriptionData> persistedData = subscriptionStore.getUserSubscriptions(userId);

            if (persistedData.isEmpty()) {
                log.debug("[订阅管理] 用户无持久化订阅数据: userId={}", userId);
                return 0;
            }

            Set<ChannelSubscription> restoredSubs = new HashSet<>();
            for (Map.Entry<String, SubscriptionData> entry : persistedData.entrySet()) {
                try {
                    String channel = entry.getKey();
                    SubscriptionData subData = entry.getValue();

                    // user: 频道不恢复，统一由连接建立时的自动订阅（BasicSubscriptionService）和
                    // 手动订阅（前端信令订阅）处理。跳过可避免 room 连接错误继承 user: 频道的订阅。
                    if (channel.startsWith(WebSocketConstants.CHANNEL_PREFIX_USER)) {
                        continue;
                    }

                    ChannelSubscription subscription = ChannelSubscription.builder()
                            .channel(channel)
                            .eventTypes(subData.getEventTypes())
                            .subscribeTime(subData.getSubscribeTime())
                            .priority(subData.getPriority())
                            .autoSubscribed("auto".equals(subData.getSource()))
                            .build();

                    restoredSubs.add(subscription);
                    log.debug("[订阅管理] 恢复订阅: userId={}, channel={}", userId, channel);

                } catch (Exception e) {
                    log.warn("[订阅管理] 恢复单个订阅失败: userId={}, error={}", userId, e.getMessage());
                }
            }

            // 合并到 userSubscriptions：保留本端已有订阅，新增 Redis 中的订阅
            userSubscriptions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).addAll(restoredSubs);

            // 重建反向索引：channelSessions / sessionSubscriptions / sessionUserMap
            sessionUserMap.put(sessionId, userId);
            sessionSubscriptions.put(sessionId, restoredSubs.stream()
                    .map(ChannelSubscription::getChannel)
                    .collect(Collectors.toSet()));
            for (ChannelSubscription sub : restoredSubs) {
                channelSessions.computeIfAbsent(sub.getChannel(), k -> ConcurrentHashMap.newKeySet()).add(sessionId);
            }

            log.info("[订阅管理] 恢复用户订阅状态: userId={}, sessionId={}, restoredCount={}, totalCount={}",
                    userId, sessionId, restoredSubs.size(),
                    userSubscriptions.getOrDefault(userId, Collections.emptySet()).size());
            return restoredSubs.size();

        } catch (Exception e) {
            log.error("[订阅管理] 恢复用户订阅状态失败: userId={}", userId, e);
            return 0;
        }
    }

    public void batchPersistAllSubscriptions() {
        try {
            if (!userSubscriptions.isEmpty()) {
                for (Map.Entry<Long, Set<ChannelSubscription>> entry : userSubscriptions.entrySet()) {
                    Long userId = entry.getKey();
                    Set<ChannelSubscription> subscriptions = entry.getValue();
                    
                    Map<String, SubscriptionData> subscriptionData = new HashMap<>();
                    for (ChannelSubscription subscription : subscriptions) {
                        subscriptionData.put(subscription.getChannel(), SubscriptionData.builder()
                                .eventTypes(subscription.getEventTypes())
                                .source(Boolean.TRUE.equals(subscription.getAutoSubscribed()) ? "auto" : "manual")
                                .subscribeTime(subscription.getSubscribeTime())
                                .priority(subscription.getPriority())
                                .build());
                    }
                    subscriptionStore.batchSaveUserSubscriptions(userId, subscriptionData);
                }
            }
            log.info("[订阅管理] 批量持久化所有活跃用户订阅完成: userCount={}", userSubscriptions.size());
        } catch (Exception e) {
            log.error("[订阅管理] 批量持久化订阅失败", e);
        }
    }

    public void cleanupUserSubscriptions(Long userId, Set<String> channels) {
        try {
            Set<ChannelSubscription> userSubs = userSubscriptions.get(userId);
            if (userSubs != null) {
                userSubs.removeIf(sub -> channels.contains(sub.getChannel()));
                if (userSubs.isEmpty()) {
                    userSubscriptions.remove(userId);
                }
            }

            for (String channel : channels) {
                Set<String> channelSessions = this.channelSessions.get(channel);
                if (channelSessions != null) {
                    Set<String> remainingSessions = channelSessions.stream()
                            .filter(s -> !channels.contains(s))
                            .collect(Collectors.toSet());
                    if (remainingSessions.isEmpty()) {
                        this.channelSessions.remove(channel);
                    } else {
                        this.channelSessions.put(channel, remainingSessions);
                    }
                }
            }

            log.info("[订阅管理] 清理用户订阅: userId={}, channelCount={}", userId, channels.size());
        } catch (Exception e) {
            log.error("[订阅管理] 清理用户订阅失败: userId={}", userId, e);
        }
    }

    public void updateSubscriptionActivity(Long userId, String channel) {
        try {
            Set<ChannelSubscription> userSubs = userSubscriptions.get(userId);
            if (userSubs != null) {
                for (ChannelSubscription sub : userSubs) {
                    if (channel.equals(sub.getChannel())) {
                        sub.updateLastActiveTime();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("[订阅管理] 更新订阅活跃时间失败: userId={}, channel={}", userId, channel, e);
        }
    }

    public void performExpiredPermissionsCleanup() {
        try {
            permissionCache.entrySet().removeIf(entry -> entry.getValue().isBefore(LocalDateTime.now()));
            log.debug("[订阅管理] 本地过期权限缓存清理完成: remaining={}", permissionCache.size());
        } catch (Exception e) {
            log.error("[订阅管理] 清理过期权限缓存失败", e);
        }
    }

    public void performDailySubscriptionCleanup() {
        try {
            long threshold = System.currentTimeMillis() - (WebSocketConstants.SUBSCRIPTION_EXPIRE_HOURS * 3600000L);
            int cleanedCount = 0;

            for (Map.Entry<Long, Set<ChannelSubscription>> entry : userSubscriptions.entrySet()) {
                Set<ChannelSubscription> cleaned = entry.getValue().stream()
                        .filter(sub -> {
                            long subTime = sub.getSubscribeTime().toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
                            return subTime > threshold;
                        })
                        .collect(Collectors.toSet());
                
                if (cleaned.isEmpty()) {
                    userSubscriptions.remove(entry.getKey());
                    cleanedCount++;
                } else {
                    entry.setValue(cleaned);
                }
            }

            log.info("[订阅管理] 每日订阅清理完成: cleanedUserCount={}", cleanedCount);
        } catch (Exception e) {
            log.error("[订阅管理] 每日订阅清理失败", e);
        }
    }

    public Map<String, Object> getSubscriptionStats() {
        try {
            return Map.of(
                    "totalUsers", userSubscriptions.size(),
                    "activeUsers", userSubscriptions.size(),
                    "totalChannels", channelSessions.size(),
                    "totalSubscriptions", userSubscriptions.values().stream().mapToInt(Set::size).sum()
            );
        } catch (Exception e) {
            log.error("[订阅管理] 获取订阅统计失败", e);
            return Map.of("error", "统计信息获取失败");
        }
    }
}
