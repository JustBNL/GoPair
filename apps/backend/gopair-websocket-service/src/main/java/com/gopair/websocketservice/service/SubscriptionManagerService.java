package com.gopair.websocketservice.service;

import com.gopair.websocketservice.constants.WebSocketConstants;
import com.gopair.websocketservice.domain.ChannelSubscription;
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

            Map<String, Object> subscriptionData = Map.of(
                    "channel", channel,
                    "eventTypes", eventTypes,
                    "source", source,
                    "subscribeTime", subscription.getSubscribeTime().toString(),
                    "priority", subscription.getPriority()
            );
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
        if (sessions == null) return Collections.emptySet();

        if (eventType == null) return new HashSet<>(sessions);

        return sessions.stream()
                .filter(sessionId -> isSessionSubscribedToEvent(sessionId, channel, eventType))
                .collect(Collectors.toSet());
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
        if (sessionChannels == null || !sessionChannels.contains(channel)) return false;

        Set<String> channelSessions = this.channelSessions.get(channel);
        if (channelSessions == null || !channelSessions.contains(sessionId)) return false;

        // 通过 sessionUserMap 精确反查该 session 对应的 userId，避免误用其他用户的订阅配置
        Long userId = sessionUserMap.get(sessionId);
        if (userId == null) {
            // sessionUserMap 中无记录时降级：允许投递，避免因内存状态不一致漏发消息
            return true;
        }

        Set<ChannelSubscription> subscriptions = userSubscriptions.get(userId);
        if (subscriptions == null) return true;

        for (ChannelSubscription sub : subscriptions) {
            if (channel.equals(sub.getChannel())) {
                Set<String> eventTypes = sub.getEventTypes();
                // eventTypes 为空表示订阅全部事件类型
                if (eventTypes == null || eventTypes.isEmpty()) return true;
                return eventTypes.contains(eventType);
            }
        }

        return false;
    }

    public int restoreUserSubscriptionState(Long userId) {
        try {
            Map<Object, Object> persistedData = subscriptionStore.getUserSubscriptions(userId);
            
            if (persistedData.isEmpty()) {
                log.debug("[订阅管理] 用户无持久化订阅数据: userId={}", userId);
                return 0;
            }
            
            Set<ChannelSubscription> persistedSubscriptions = new HashSet<>();
            for (Map.Entry<Object, Object> entry : persistedData.entrySet()) {
                try {
                    String channel = (String) entry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> subData = (Map<String, Object>) entry.getValue();
                    
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

            userSubscriptions.put(userId, persistedSubscriptions);
            
            log.info("[订阅管理] 恢复用户订阅状态: userId={}, count={}", userId, persistedSubscriptions.size());
            return persistedSubscriptions.size();
            
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
                            .filter(s -> !channels.contains(channel))
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
