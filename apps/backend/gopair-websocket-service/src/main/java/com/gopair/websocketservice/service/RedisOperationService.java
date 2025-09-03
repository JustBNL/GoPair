package com.gopair.websocketservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.gopair.websocketservice.constants.WebSocketConstants;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis操作服务 - 集中化所有Redis操作
 * 
 * @author gopair
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisOperationService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    // Redis键前缀常量（本地特有的，公共常量使用WebSocketConstants）
    private static final String USER_RECENT_ROOMS_PREFIX = "ws:user:recent-rooms:";
    private static final String SESSION_INFO_PREFIX = "ws:session:info:";
    private static final String PERMISSION_CACHE_PREFIX = "ws:permission:cache:";
    private static final String RATE_LIMIT_PREFIX = "ws:rate:limit:";
    private static final String PERFORMANCE_METRICS_PREFIX = "ws:performance:metrics:";
    private static final String GLOBAL_STATS_KEY = "ws:global:stats";
    
    // 过期时间常量（本地特有的，公共常量使用WebSocketConstants）
    private static final long RECENT_ROOMS_EXPIRE_HOURS = 72;
    private static final long PERFORMANCE_METRICS_EXPIRE_HOURS = 168; // 7天
    
    // ==================== 订阅状态管理 ====================
    
    /**
     * 保存用户订阅状态
     */
    public void saveUserSubscription(Long userId, String channel, Map<String, Object> subscriptionData) {
        try {
            String redisKey = WebSocketConstants.USER_SUBSCRIPTIONS_PREFIX + userId;
            redisTemplate.opsForHash().put(redisKey, channel, subscriptionData);
            redisTemplate.expire(redisKey, WebSocketConstants.SUBSCRIPTION_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("[Redis操作] 保存用户订阅: userId={}, channel={}", userId, channel);
        } catch (Exception e) {
            log.error("[Redis操作] 保存用户订阅失败: userId={}, channel={}, error={}", userId, channel, e.getMessage());
        }
    }
    
    /**
     * 移除用户订阅
     */
    public void removeUserSubscription(Long userId, String channel) {
        try {
            String redisKey = WebSocketConstants.USER_SUBSCRIPTIONS_PREFIX + userId;
            redisTemplate.opsForHash().delete(redisKey, channel);
            log.debug("[Redis操作] 移除用户订阅: userId={}, channel={}", userId, channel);
        } catch (Exception e) {
            log.error("[Redis操作] 移除用户订阅失败: userId={}, channel={}, error={}", userId, channel, e.getMessage());
        }
    }
    
    /**
     * 获取用户所有订阅
     */
    public Map<Object, Object> getUserSubscriptions(Long userId) {
        try {
            String redisKey = WebSocketConstants.USER_SUBSCRIPTIONS_PREFIX + userId;
            return redisTemplate.opsForHash().entries(redisKey);
        } catch (Exception e) {
            log.error("[Redis操作] 获取用户订阅失败: userId={}, error={}", userId, e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * 批量保存用户订阅
     */
    public void batchSaveUserSubscriptions(Long userId, Map<String, Object> subscriptions) {
        try {
            if (subscriptions.isEmpty()) return;
            
            String redisKey = WebSocketConstants.USER_SUBSCRIPTIONS_PREFIX + userId;
            redisTemplate.opsForHash().putAll(redisKey, subscriptions);
            redisTemplate.expire(redisKey, WebSocketConstants.SUBSCRIPTION_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("[Redis操作] 批量保存用户订阅: userId={}, count={}", userId, subscriptions.size());
        } catch (Exception e) {
            log.error("[Redis操作] 批量保存用户订阅失败: userId={}, error={}", userId, e.getMessage());
        }
    }
    
    // ==================== 最近房间管理 ====================
    
    /**
     * 更新用户最近访问的房间
     */
    public void updateUserRecentRooms(Long userId, Long roomId) {
        try {
            String redisKey = USER_RECENT_ROOMS_PREFIX + userId;
            Map<String, Object> roomInfo = Map.of(
                "roomId", roomId,
                "accessTime", LocalDateTime.now().toString(),
                "timestamp", System.currentTimeMillis()
            );
            redisTemplate.opsForHash().put(redisKey, roomId.toString(), roomInfo);
            redisTemplate.expire(redisKey, RECENT_ROOMS_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("[Redis操作] 更新最近房间: userId={}, roomId={}", userId, roomId);
        } catch (Exception e) {
            log.error("[Redis操作] 更新最近房间失败: userId={}, roomId={}, error={}", userId, roomId, e.getMessage());
        }
    }
    
    /**
     * 获取用户最近访问的房间
     */
    public List<Map<String, Object>> getUserRecentRooms(Long userId, int limit) {
        try {
            String redisKey = USER_RECENT_ROOMS_PREFIX + userId;
            Map<Object, Object> allRooms = redisTemplate.opsForHash().entries(redisKey);
            
            return allRooms.values().stream()
                .map(obj -> (Map<String, Object>) obj)
                .sorted((a, b) -> Long.compare(
                    (Long) b.get("timestamp"), 
                    (Long) a.get("timestamp")
                ))
                .limit(limit)
                .toList();
        } catch (Exception e) {
            log.error("[Redis操作] 获取最近房间失败: userId={}, error={}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // ==================== 会话信息管理 ====================
    
    /**
     * 保存会话信息
     */
    public void saveSessionInfo(String sessionId, Map<String, Object> sessionInfo) {
        try {
            String redisKey = SESSION_INFO_PREFIX + sessionId;
            redisTemplate.opsForHash().putAll(redisKey, sessionInfo);
            redisTemplate.expire(redisKey, 4, TimeUnit.HOURS); // 会话信息4小时过期
            log.debug("[Redis操作] 保存会话信息: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("[Redis操作] 保存会话信息失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }
    
    /**
     * 获取会话信息
     */
    public Map<Object, Object> getSessionInfo(String sessionId) {
        try {
            String redisKey = SESSION_INFO_PREFIX + sessionId;
            return redisTemplate.opsForHash().entries(redisKey);
        } catch (Exception e) {
            log.error("[Redis操作] 获取会话信息失败: sessionId={}, error={}", sessionId, e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * 删除会话信息
     */
    public void removeSessionInfo(String sessionId) {
        try {
            String redisKey = SESSION_INFO_PREFIX + sessionId;
            redisTemplate.delete(redisKey);
            log.debug("[Redis操作] 删除会话信息: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("[Redis操作] 删除会话信息失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }
    
    // ==================== 权限缓存管理 ====================
    
    /**
     * 缓存用户权限
     */
    public void cacheUserPermission(Long userId, String resource, boolean hasPermission) {
        try {
            String redisKey = PERMISSION_CACHE_PREFIX + userId + ":" + resource;
            redisTemplate.opsForValue().set(redisKey, hasPermission, WebSocketConstants.PERMISSION_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("[Redis操作] 缓存用户权限: userId={}, resource={}, hasPermission={}", userId, resource, hasPermission);
        } catch (Exception e) {
            log.error("[Redis操作] 缓存用户权限失败: userId={}, resource={}, error={}", userId, resource, e.getMessage());
        }
    }
    
    /**
     * 获取缓存的用户权限
     */
    public Optional<Boolean> getCachedUserPermission(Long userId, String resource) {
        try {
            String redisKey = PERMISSION_CACHE_PREFIX + userId + ":" + resource;
            Boolean permission = (Boolean) redisTemplate.opsForValue().get(redisKey);
            return Optional.ofNullable(permission);
        } catch (Exception e) {
            log.error("[Redis操作] 获取缓存权限失败: userId={}, resource={}, error={}", userId, resource, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * 清除用户权限缓存
     */
    public void clearUserPermissionCache(Long userId) {
        try {
            String pattern = PERMISSION_CACHE_PREFIX + userId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("[Redis操作] 清除用户权限缓存: userId={}, count={}", userId, keys.size());
            }
        } catch (Exception e) {
            log.error("[Redis操作] 清除用户权限缓存失败: userId={}, error={}", userId, e.getMessage());
        }
    }
    
    // ==================== 限流数据管理 ====================
    
    /**
     * 增加限流计数
     */
    public long incrementRateLimit(String key, long windowSeconds) {
        try {
            String redisKey = RATE_LIMIT_PREFIX + key;
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1) {
                redisTemplate.expire(redisKey, windowSeconds, TimeUnit.SECONDS);
            }
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("[Redis操作] 增加限流计数失败: key={}, error={}", key, e.getMessage());
            return 0;
        }
    }
    
    /**
     * 获取当前限流计数
     */
    public long getRateLimitCount(String key) {
        try {
            String redisKey = RATE_LIMIT_PREFIX + key;
            Integer count = (Integer) redisTemplate.opsForValue().get(redisKey);
            return count != null ? count.longValue() : 0;
        } catch (Exception e) {
            log.error("[Redis操作] 获取限流计数失败: key={}, error={}", key, e.getMessage());
            return 0;
        }
    }
    
    /**
     * 清理过期的限流数据
     */
    public void cleanupExpiredRateLimits() {
        try {
            String pattern = RATE_LIMIT_PREFIX + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                // Redis的过期机制会自动清理，这里只是记录
                log.debug("[Redis操作] 限流数据清理检查: totalKeys={}", keys.size());
            }
        } catch (Exception e) {
            log.error("[Redis操作] 清理限流数据失败: error={}", e.getMessage());
        }
    }
    
    // ==================== 性能监控数据管理 ====================
    
    /**
     * 保存性能指标
     */
    public void savePerformanceMetrics(String metricsKey, Map<String, Object> metrics) {
        try {
            String redisKey = PERFORMANCE_METRICS_PREFIX + metricsKey;
            redisTemplate.opsForHash().putAll(redisKey, metrics);
            redisTemplate.expire(redisKey, PERFORMANCE_METRICS_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("[Redis操作] 保存性能指标: key={}, metrics={}", metricsKey, metrics.size());
        } catch (Exception e) {
            log.error("[Redis操作] 保存性能指标失败: key={}, error={}", metricsKey, e.getMessage());
        }
    }
    
    /**
     * 获取性能指标
     */
    public Map<Object, Object> getPerformanceMetrics(String metricsKey) {
        try {
            String redisKey = PERFORMANCE_METRICS_PREFIX + metricsKey;
            return redisTemplate.opsForHash().entries(redisKey);
        } catch (Exception e) {
            log.error("[Redis操作] 获取性能指标失败: key={}, error={}", metricsKey, e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * 保存全局统计数据
     */
    public void saveGlobalStats(Map<String, Object> stats) {
        try {
            redisTemplate.opsForHash().putAll(GLOBAL_STATS_KEY, stats);
            redisTemplate.expire(GLOBAL_STATS_KEY, 1, TimeUnit.HOURS);
            log.debug("[Redis操作] 保存全局统计: stats={}", stats.size());
        } catch (Exception e) {
            log.error("[Redis操作] 保存全局统计失败: error={}", e.getMessage());
        }
    }
    
    /**
     * 获取全局统计数据
     */
    public Map<Object, Object> getGlobalStats() {
        try {
            return redisTemplate.opsForHash().entries(GLOBAL_STATS_KEY);
        } catch (Exception e) {
            log.error("[Redis操作] 获取全局统计失败: error={}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    // ==================== 通用操作 ====================
    
    /**
     * 检查Redis连接状态
     */
    public boolean isRedisAvailable() {
        try {
            redisTemplate.opsForValue().get("ping");
            return true;
        } catch (Exception e) {
            log.warn("[Redis操作] Redis连接检查失败: error={}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 清理所有过期数据
     */
    public void cleanupAllExpiredData() {
        try {
            // 清理过期的限流数据
            cleanupExpiredRateLimits();
            
            // 清理过期的性能指标
            String pattern = PERFORMANCE_METRICS_PREFIX + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                log.debug("[Redis操作] 性能指标清理检查: totalKeys={}", keys.size());
            }
            
            log.info("[Redis操作] Redis数据清理完成");
        } catch (Exception e) {
            log.error("[Redis操作] Redis数据清理失败: error={}", e.getMessage());
        }
    }
    
    /**
     * 获取Redis统计信息
     */
    public Map<String, Object> getRedisStats() {
        try {
            return Map.of(
                "available", isRedisAvailable(),
                "timestamp", LocalDateTime.now().toString(),
                "subscriptionKeys", getKeyCount(WebSocketConstants.USER_SUBSCRIPTIONS_PREFIX + "*"),
                "recentRoomsKeys", getKeyCount(USER_RECENT_ROOMS_PREFIX + "*"),
                "sessionKeys", getKeyCount(SESSION_INFO_PREFIX + "*"),
                "permissionKeys", getKeyCount(PERMISSION_CACHE_PREFIX + "*"),
                "rateLimitKeys", getKeyCount(RATE_LIMIT_PREFIX + "*"),
                "metricsKeys", getKeyCount(PERFORMANCE_METRICS_PREFIX + "*")
            );
        } catch (Exception e) {
            log.error("[Redis操作] 获取Redis统计失败: error={}", e.getMessage());
            return Map.of("available", false, "error", e.getMessage());
        }
    }
    
    /**
     * 获取指定模式的键数量
     */
    private long getKeyCount(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.warn("[Redis操作] 获取键数量失败: pattern={}, error={}", pattern, e.getMessage());
            return 0;
        }
    }

    /**
     * 每分钟轻量级Redis检查任务
     * 迁移自ScheduledTaskService
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60000) // 1分钟
    public void lightweightRedisCheckTask() {
        log.debug("[Redis操作] 开始执行1分钟Redis检查...");
        
        try {
            // 1. 更新全局统计
            updateGlobalStatistics();
            
            // 2. 检查Redis连接状态
            boolean redisAvailable = isRedisAvailable();
            if (!redisAvailable) {
                log.warn("[Redis操作] Redis连接不可用，请检查连接状态");
            }
            
            log.debug("[Redis操作] 1分钟Redis检查完成");
        } catch (Exception e) {
            log.error("[Redis操作] 1分钟Redis检查失败", e);
        }
    }

    /**
     * 每小时Redis数据清理任务
     * 迁移自ScheduledTaskService
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 3600000) // 1小时
    public void hourlyRedisCleanupTask() {
        log.info("[Redis操作] 开始执行每小时Redis清理任务...");
        
        try {
            cleanupAllExpiredData();
            log.info("[Redis操作] 每小时Redis清理任务完成");
        } catch (Exception e) {
            log.error("[Redis操作] 每小时Redis清理任务失败", e);
        }
    }

    /**
     * 每日深度Redis清理任务
     * 迁移自ScheduledTaskService
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点
    public void dailyDeepRedisCleanupTask() {
        log.info("[Redis操作] 开始执行每日深度Redis清理任务...");
        
        try {
            // 深度清理Redis过期数据
            cleanupAllExpiredData();
            
            // 清理统计数据
            cleanupOldStatistics();
            
            log.info("[Redis操作] 每日深度Redis清理任务完成");
        } catch (Exception e) {
            log.error("[Redis操作] 每日深度Redis清理任务失败", e);
        }
    }

    /**
     * 更新全局统计信息
     * 迁移自ScheduledTaskService
     */
    private void updateGlobalStatistics() {
        try {
            // 获取Redis统计数据
            Map<String, Object> redisStats = getRedisStats();
            
            // 合并统计信息
            Map<String, Object> globalStats = Map.of(
                "timestamp", java.time.LocalDateTime.now().toString(),
                "redis", redisStats,
                "performance", Map.of() // 简化版本
            );
            
            // 保存到Redis
            saveGlobalStats(globalStats);
            
        } catch (Exception e) {
            log.error("[Redis操作] 更新全局统计失败", e);
        }
    }

    /**
     * 清理旧的统计数据
     */
    private void cleanupOldStatistics() {
        try {
            // 清理7天前的统计数据
            String statsKeyPattern = "gopair:stats:*";
            Set<String> keys = redisTemplate.keys(statsKeyPattern);
            
            if (keys != null && !keys.isEmpty()) {
                int deletedCount = 0;
                for (String key : keys) {
                    // 检查key的时间戳，删除7天前的数据
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

    /**
     * 检查是否为旧的统计数据键
     */
    private boolean isOldStatisticsKey(String key) {
        try {
            // 简化版：检查键的过期时间
            Long ttl = redisTemplate.getExpire(key);
            return ttl != null && ttl < 0; // 已过期的键
        } catch (Exception e) {
            return false;
        }
    }
} 