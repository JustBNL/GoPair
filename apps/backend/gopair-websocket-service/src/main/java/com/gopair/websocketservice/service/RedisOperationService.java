package com.gopair.websocketservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.gopair.websocketservice.constants.WebSocketConstants;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisOperationService implements SessionStore, SubscriptionStore, RateLimitStore {

    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 统一的会话信息前缀，作为在线状态的权威来源
     */
    private static final String SESSION_PREFIX = "ws:session:";
    /**
     * 用户维度的会话索引前缀
     */
    private static final String USER_SESSIONS_PREFIX = "ws:user-sessions:";
    private static final String PERMISSION_CACHE_PREFIX = "ws:permission:cache:";
    private static final String RATE_LIMIT_PREFIX = "ws:rate:limit:";
    private static final String GLOBAL_STATS_KEY = "ws:global:stats";
    
    public void saveUserSubscription(Long userId, String channel, Map<String, Object> subscriptionData) {
        try {
            String redisKey = WebSocketConstants.USER_SUBSCRIPTIONS_PREFIX + userId;
            redisTemplate.opsForHash().put(redisKey, channel, subscriptionData);
            redisTemplate.expire(redisKey, WebSocketConstants.SUBSCRIPTION_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("[Redis操作] 保存用户订阅: userId={}, channel={}", userId, channel);
        } catch (Exception e) {
            log.error("[Redis操作] 保存用户订阅失败: userId={}, channel={}", userId, channel, e);
        }
    }
    
    public void removeUserSubscription(Long userId, String channel) {
        try {
            String redisKey = WebSocketConstants.USER_SUBSCRIPTIONS_PREFIX + userId;
            redisTemplate.opsForHash().delete(redisKey, channel);
            log.debug("[Redis操作] 移除用户订阅: userId={}, channel={}", userId, channel);
        } catch (Exception e) {
            log.error("[Redis操作] 移除用户订阅失败: userId={}, channel={}", userId, channel, e);
        }
    }
    
    public Map<Object, Object> getUserSubscriptions(Long userId) {
        try {
            String redisKey = WebSocketConstants.USER_SUBSCRIPTIONS_PREFIX + userId;
            return redisTemplate.opsForHash().entries(redisKey);
        } catch (Exception e) {
            log.error("[Redis操作] 获取用户订阅失败: userId={}", userId, e);
            return new HashMap<>();
        }
    }
    
    public void batchSaveUserSubscriptions(Long userId, Map<String, Object> subscriptions) {
        try {
            if (subscriptions.isEmpty()) return;
            
            String redisKey = WebSocketConstants.USER_SUBSCRIPTIONS_PREFIX + userId;
            redisTemplate.opsForHash().putAll(redisKey, subscriptions);
            redisTemplate.expire(redisKey, WebSocketConstants.SUBSCRIPTION_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("[Redis操作] 批量保存用户订阅: userId={}, count={}", userId, subscriptions.size());
        } catch (Exception e) {
            log.error("[Redis操作] 批量保存用户订阅失败: userId={}", userId, e);
        }
    }
    
    /**
     * 保存会话信息（统一使用 ws:session:{sessionId}）
     */
    public void saveSession(String sessionId,
                            Long userId,
                            String connectionType,
                            Long roomId,
                            String instanceId,
                            long ttlSeconds) {
        try {
            String redisKey = SESSION_PREFIX + sessionId;
            Map<String, Object> sessionInfo = new HashMap<>();
            sessionInfo.put("userId", userId);
            sessionInfo.put("connectionType", connectionType);
            sessionInfo.put("roomId", roomId == null ? "" : roomId.toString());
            // 统一使用 UTC epoch 秒存储时间戳，避免时区歧义
            sessionInfo.put("connectTime", Instant.now().getEpochSecond());
            sessionInfo.put("lastActiveTime", Instant.now().getEpochSecond());
            sessionInfo.put("instanceId", instanceId);

            redisTemplate.opsForHash().putAll(redisKey, sessionInfo);
            redisTemplate.expire(redisKey, ttlSeconds, TimeUnit.SECONDS);
            log.debug("[Redis操作] 保存会话: sessionId={}, userId={}, type={}", sessionId, userId, connectionType);
        } catch (Exception e) {
            log.error("[Redis操作] 保存会话失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 刷新会话 TTL 与最近活跃时间
     */
    public void refreshSessionTtl(String sessionId, long ttlSeconds) {
        try {
            String redisKey = SESSION_PREFIX + sessionId;
            redisTemplate.expire(redisKey, ttlSeconds, TimeUnit.SECONDS);
            redisTemplate.opsForHash().put(redisKey, "lastActiveTime", Instant.now().getEpochSecond());
            log.debug("[Redis操作] 刷新会话TTL: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("[Redis操作] 刷新会话TTL失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 删除会话
     */
    public void removeSession(String sessionId) {
        try {
            String redisKey = SESSION_PREFIX + sessionId;
            redisTemplate.delete(redisKey);
            log.debug("[Redis操作] 删除会话: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("[Redis操作] 删除会话失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 读取会话信息（与 {@link #saveSession(String, Long, String, Long, String, long)} 对应）。
     * 返回的 Map 仅包含业务字段，不保证类型转换；上层可以根据需要封装为强类型对象。
     */
    public Map<Object, Object> getSession(String sessionId) {
        try {
            String redisKey = SESSION_PREFIX + sessionId;
            Map<Object, Object> sessionInfo = redisTemplate.opsForHash().entries(redisKey);
            log.debug("[Redis操作] 读取会话: sessionId={}, fields={}", sessionId, sessionInfo.size());
            return sessionInfo;
        } catch (Exception e) {
            log.error("[Redis操作] 读取会话失败: sessionId={}", sessionId, e);
            return new HashMap<>();
        }
    }

    /**
     * 为用户添加会话索引
     */
    public void addUserSession(Long userId, String sessionId) {
        try {
            String redisKey = USER_SESSIONS_PREFIX + userId;
            redisTemplate.opsForSet().add(redisKey, sessionId);
            log.debug("[Redis操作] 添加用户会话索引: userId={}, sessionId={}", userId, sessionId);
        } catch (Exception e) {
            log.error("[Redis操作] 添加用户会话索引失败: userId={}, sessionId={}", userId, sessionId, e);
        }
    }

    /**
     * 为用户移除会话索引
     */
    public void removeUserSession(Long userId, String sessionId) {
        try {
            String redisKey = USER_SESSIONS_PREFIX + userId;
            redisTemplate.opsForSet().remove(redisKey, sessionId);
            log.debug("[Redis操作] 移除用户会话索引: userId={}, sessionId={}", userId, sessionId);
        } catch (Exception e) {
            log.error("[Redis操作] 移除用户会话索引失败: userId={}, sessionId={}", userId, sessionId, e);
        }
    }

    /**
     * 获取用户的所有会话ID
     */
    public Set<String> getUserSessions(Long userId) {
        try {
            String redisKey = USER_SESSIONS_PREFIX + userId;
            Set<Object> members = redisTemplate.opsForSet().members(redisKey);
            if (members == null || members.isEmpty()) {
                return Collections.emptySet();
            }
            Set<String> result = new HashSet<>();
            for (Object member : members) {
                if (member != null) {
                    result.add(member.toString());
                }
            }
            return result;
        } catch (Exception e) {
            log.error("[Redis操作] 获取用户会话列表失败: userId={}", userId, e);
            return Collections.emptySet();
        }
    }
    
    public void cacheUserPermission(Long userId, String resource, boolean hasPermission) {
        try {
            String redisKey = PERMISSION_CACHE_PREFIX + userId + ":" + resource;
            redisTemplate.opsForValue().set(redisKey, hasPermission, WebSocketConstants.PERMISSION_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("[Redis操作] 缓存用户权限: userId={}, resource={}, hasPermission={}", userId, resource, hasPermission);
        } catch (Exception e) {
            log.error("[Redis操作] 缓存用户权限失败: userId={}, resource={}", userId, resource, e);
        }
    }
    
    public Optional<Boolean> getCachedUserPermission(Long userId, String resource) {
        try {
            String redisKey = PERMISSION_CACHE_PREFIX + userId + ":" + resource;
            Boolean permission = (Boolean) redisTemplate.opsForValue().get(redisKey);
            return Optional.ofNullable(permission);
        } catch (Exception e) {
            log.error("[Redis操作] 获取缓存权限失败: userId={}, resource={}", userId, resource, e);
            return Optional.empty();
        }
    }
    
    public void clearUserPermissionCache(Long userId) {
        try {
            String pattern = PERMISSION_CACHE_PREFIX + userId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("[Redis操作] 清除用户权限缓存: userId={}, count={}", userId, keys.size());
            }
        } catch (Exception e) {
            log.error("[Redis操作] 清除用户权限缓存失败: userId={}", userId, e);
        }
    }
    
    public long incrementRateLimit(String key, long windowSeconds) {
        try {
            String redisKey = RATE_LIMIT_PREFIX + key;
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1) {
                redisTemplate.expire(redisKey, windowSeconds, TimeUnit.SECONDS);
            }
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("[Redis操作] 增加限流计数失败: key={}", key, e);
            return 0;
        }
    }
    
    public long getRateLimitCount(String key) {
        try {
            String redisKey = RATE_LIMIT_PREFIX + key;
            Integer count = (Integer) redisTemplate.opsForValue().get(redisKey);
            return count != null ? count.longValue() : 0;
        } catch (Exception e) {
            log.error("[Redis操作] 获取限流计数失败: key={}", key, e);
            return 0;
        }
    }
    
    /**
     * 重置指定限流键的计数。
     *
     * 该方法仅删除对应的 Redis 键，不会影响其它业务数据。
     *
     * @param key 限流业务键（不包含 {@link #RATE_LIMIT_PREFIX} 前缀）
     */
    public void resetRateLimit(String key) {
        try {
            String redisKey = RATE_LIMIT_PREFIX + key;
            redisTemplate.delete(redisKey);
            log.debug("[Redis操作] 重置限流计数: key={}", key);
        } catch (Exception e) {
            log.error("[Redis操作] 重置限流计数失败: key={}", key, e);
        }
    }
    
    public void cleanupExpiredRateLimits() {
        try {
            String pattern = RATE_LIMIT_PREFIX + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                log.debug("[Redis操作] 限流数据清理检查: totalKeys={}", keys.size());
            }
        } catch (Exception e) {
            log.error("[Redis操作] 清理限流数据失败", e);
        }
    }
    
    public void saveGlobalStats(Map<String, Object> stats) {
        try {
            redisTemplate.opsForHash().putAll(GLOBAL_STATS_KEY, stats);
            redisTemplate.expire(GLOBAL_STATS_KEY, 1, TimeUnit.HOURS);
            log.debug("[Redis操作] 保存全局统计: stats={}", stats.size());
        } catch (Exception e) {
            log.error("[Redis操作] 保存全局统计失败", e);
        }
    }
    
    public Map<Object, Object> getGlobalStats() {
        try {
            return redisTemplate.opsForHash().entries(GLOBAL_STATS_KEY);
        } catch (Exception e) {
            log.error("[Redis操作] 获取全局统计失败", e);
            return new HashMap<>();
        }
    }
    
    public boolean isRedisAvailable() {
        try {
            redisTemplate.opsForValue().get("ping");
            return true;
        } catch (Exception e) {
            log.warn("[Redis操作] Redis连接检查失败: error={}", e.getMessage());
            return false;
        }
    }
    
    public void cleanupAllExpiredData() {
        try {
            cleanupExpiredRateLimits();
            log.info("[Redis操作] Redis数据清理完成");
        } catch (Exception e) {
            log.error("[Redis操作] Redis数据清理失败", e);
        }
    }
    
    public Map<String, Object> getRedisStats() {
        try {
            return Map.of(
                "available", isRedisAvailable(),
                "timestamp", LocalDateTime.now().toString(),
                "subscriptionKeys", getKeyCount(WebSocketConstants.USER_SUBSCRIPTIONS_PREFIX + "*"),
                "sessionKeys", getKeyCount(SESSION_PREFIX + "*"),
                "permissionKeys", getKeyCount(PERMISSION_CACHE_PREFIX + "*"),
                "rateLimitKeys", getKeyCount(RATE_LIMIT_PREFIX + "*")
            );
        } catch (Exception e) {
            log.error("[Redis操作] 获取Redis统计失败", e);
            return Map.of("available", false, "error", e.getMessage());
        }
    }
    
    private long getKeyCount(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.warn("[Redis操作] 获取键数量失败: pattern={}", pattern, e);
            return 0;
        }
    }

    public void performLightweightRedisCheck() {
        log.debug("[Redis操作] 开始执行轻量级Redis检查...");
        try {
            updateGlobalStatistics();
            boolean redisAvailable = isRedisAvailable();
            if (!redisAvailable) {
                log.warn("[Redis操作] Redis连接不可用，请检查连接状态");
            }
            log.debug("[Redis操作] 轻量级Redis检查完成");
        } catch (Exception e) {
            log.error("[Redis操作] 轻量级Redis检查失败", e);
        }
    }

    public void performHourlyRedisCleanup() {
        log.info("[Redis操作] 开始执行每小时Redis清理...");
        try {
            cleanupAllExpiredData();
            log.info("[Redis操作] 每小时Redis清理完成");
        } catch (Exception e) {
            log.error("[Redis操作] 每小时Redis清理失败", e);
        }
    }

    public void performDailyDeepRedisCleanup() {
        log.info("[Redis操作] 开始执行每日深度Redis清理...");
        try {
            cleanupAllExpiredData();
            cleanupOldStatistics();
            log.info("[Redis操作] 每日深度Redis清理完成");
        } catch (Exception e) {
            log.error("[Redis操作] 每日深度Redis清理失败", e);
        }
    }

    private void updateGlobalStatistics() {
        try {
            Map<String, Object> redisStats = getRedisStats();
            Map<String, Object> globalStats = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "redis", redisStats
            );
            saveGlobalStats(globalStats);
        } catch (Exception e) {
            log.error("[Redis操作] 更新全局统计失败", e);
        }
    }

    private void cleanupOldStatistics() {
        try {
            String statsKeyPattern = "gopair:stats:*";
            Set<String> keys = redisTemplate.keys(statsKeyPattern);
            
            if (keys != null && !keys.isEmpty()) {
                int deletedCount = 0;
                for (String key : keys) {
                    if (isOldStatisticsKey(key)) {
                        redisTemplate.delete(key);
                        deletedCount++;
                    }
                }
                log.info("[Redis操作] 清理旧统计数据完成，删除 {} 个键", deletedCount);
            }
        } catch (Exception e) {
            log.error("[Redis操作] 清理旧统计数据失败", e);
        }
    }

    private boolean isOldStatisticsKey(String key) {
        try {
            Long ttl = redisTemplate.getExpire(key);
            return ttl != null && ttl < 0;
        } catch (Exception e) {
            return false;
        }
    }
}
