package com.gopair.websocketservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.gopair.websocketservice.constants.WebSocketConstants;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基础限流服务
 * 简化的频率限制实现，只保留核心限流功能
 * 
 * @author gopair
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BasicRateLimitService {

    private final RedisOperationService redisOperationService;

    // 本地限流缓存
    private final Map<String, AtomicInteger> userMessageCount = new ConcurrentHashMap<>();
    private final Map<String, Long> userLastMessageTime = new ConcurrentHashMap<>();

    // 注意：限制配置常量现在使用WebSocketConstants中的统一定义

    /**
     * 检查用户消息发送频率限制
     * 
     * @param userId 用户ID
     * @return true=允许发送, false=超出限制
     */
    public boolean checkMessageRateLimit(Long userId) {
        try {
            String userKey = "user:" + userId;
            long currentTime = System.currentTimeMillis();
            
            // 检查本地限流
            Long lastMessageTime = userLastMessageTime.get(userKey);
            if (lastMessageTime != null && (currentTime - lastMessageTime) < 1000) {
                AtomicInteger count = userMessageCount.computeIfAbsent(userKey, k -> new AtomicInteger(0));
                
                if (count.incrementAndGet() > WebSocketConstants.MAX_MESSAGES_PER_SECOND) {
                    log.warn("[限流控制] 用户消息发送频率超限: userId={}, rate={}/s", 
                            userId, count.get());
                    return false;
                }
            } else {
                // 重置计数器
                userMessageCount.put(userKey, new AtomicInteger(1));
                userLastMessageTime.put(userKey, currentTime);
            }

            log.debug("[限流控制] 用户消息频率检查通过: userId={}", userId);
            return true;
            
        } catch (Exception e) {
            log.error("[限流控制] 检查消息频率限制失败: userId={}", userId, e);
            // 异常情况下允许发送，避免影响正常功能
            return true;
        }
    }

    /**
     * 检查房间连接数限制（简化版本）
     * 
     * @param roomId 房间ID
     * @param currentConnections 当前连接数
     * @return true=允许连接, false=超出限制
     */
    public boolean checkRoomConnectionLimit(Long roomId, int currentConnections) {
        try {
            if (currentConnections >= WebSocketConstants.MAX_CONNECTIONS_PER_ROOM) {
                log.warn("[限流控制] 房间连接数超限: roomId={}, connections={}, max={}", 
                        roomId, currentConnections, WebSocketConstants.MAX_CONNECTIONS_PER_ROOM);
                return false;
            }

            log.debug("[限流控制] 房间连接数检查通过: roomId={}, connections={}", 
                    roomId, currentConnections);
            return true;
            
        } catch (Exception e) {
            log.error("[限流控制] 检查房间连接数限制失败: roomId={}", roomId, e);
            return true; // 异常情况下允许连接
        }
    }

    /**
     * 检查用户订阅数限制
     * 
     * @param userId 用户ID
     * @param currentSubscriptionCount 当前订阅数
     * @return true=允许订阅, false=超出限制
     */
    public boolean checkSubscriptionLimit(Long userId, int currentSubscriptionCount) {
        try {
            if (currentSubscriptionCount >= WebSocketConstants.MAX_SUBSCRIPTIONS_PER_USER) {
                log.warn("[限流控制] 用户订阅数超限: userId={}, current={}, max={}", 
                        userId, currentSubscriptionCount, WebSocketConstants.MAX_SUBSCRIPTIONS_PER_USER);
                return false;
            }

            log.debug("[限流控制] 用户订阅数检查通过: userId={}, count={}", 
                    userId, currentSubscriptionCount);
            return true;
            
        } catch (Exception e) {
            log.error("[限流控制] 检查订阅数限制失败: userId={}", userId, e);
            return true; // 异常情况下允许订阅
        }
    }

    /**
     * 获取用户当前消息发送频率
     * 
     * @param userId 用户ID
     * @return 每秒消息数
     */
    public int getUserMessageRate(Long userId) {
        try {
            String userKey = "user:" + userId;
            AtomicInteger count = userMessageCount.get(userKey);
            int rate = count != null ? count.get() : 0;
            
            log.debug("[限流控制] 获取用户消息频率: userId={}, rate={}", userId, rate);
            return rate;
            
        } catch (Exception e) {
            log.error("[限流控制] 获取用户消息频率失败: userId={}", userId, e);
            return 0;
        }
    }

    /**
     * 重置用户消息频率限制
     * 
     * @param userId 用户ID
     */
    public void resetUserMessageRate(Long userId) {
        try {
            String userKey = "user:" + userId;
            userMessageCount.remove(userKey);
            userLastMessageTime.remove(userKey);
            
            log.info("[限流控制] 重置用户消息频率: userId={}", userId);
            
        } catch (Exception e) {
            log.error("[限流控制] 重置用户消息频率失败: userId={}", userId, e);
        }
    }

    /**
     * 获取基础限流统计信息
     * 
     * @return 统计信息Map
     */
    public Map<String, Object> getBasicRateLimitStats() {
        try {
            Map<String, Object> stats = Map.of(
                                "maxMessagesPerSecond", WebSocketConstants.MAX_MESSAGES_PER_SECOND,
            "maxConnectionsPerRoom", WebSocketConstants.MAX_CONNECTIONS_PER_ROOM,
            "maxSubscriptionsPerUser", WebSocketConstants.MAX_SUBSCRIPTIONS_PER_USER,
                    "activeUserLimits", userMessageCount.size()
            );
            
            log.debug("[限流控制] 获取基础限流统计: {}", stats);
            return stats;
            
        } catch (Exception e) {
            log.error("[限流控制] 获取基础限流统计失败", e);
            return Map.of("error", "统计信息获取失败");
        }
    }

    /**
     * 清理过期的限流数据
     */
    public void cleanupExpiredRateLimits() {
        try {
            long currentTime = System.currentTimeMillis();
            int cleanedCount = 0;
            
            // 清理超过10秒未活动的用户限流数据
            userLastMessageTime.entrySet().removeIf(entry -> {
                if (currentTime - entry.getValue() > 10000) { // 10秒
                    userMessageCount.remove(entry.getKey());
                    return true;
                }
                return false;
            });
            
            log.debug("[限流控制] 清理过期限流数据完成: count={}", cleanedCount);
            
        } catch (Exception e) {
            log.error("[限流控制] 清理过期限流数据失败", e);
        }
    }
} 