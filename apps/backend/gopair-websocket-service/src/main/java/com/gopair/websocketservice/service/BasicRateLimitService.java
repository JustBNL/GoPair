package com.gopair.websocketservice.service;

import com.gopair.websocketservice.constants.WebSocketConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 基础频率限制服务
 *
 * 使用 Redis 作为限流计数存储后端，基于 {@link RateLimitStore} 提供的
 * 统一限流计数能力，对单个用户的消息发送频率进行限制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BasicRateLimitService {

    private static final long MESSAGE_RATE_LIMIT_WINDOW_SECONDS = 1L;
    private static final String MESSAGE_RATE_LIMIT_USER_KEY_PREFIX = "msg:user:";

    private final RateLimitStore rateLimitStore;

    /**
     * 检查用户消息发送是否触发频率限制。
     *
     * 逻辑说明：
     * - 使用 Redis 计数窗口（当前为 1 秒窗口）
     * - 计数超过 {@link WebSocketConstants#MAX_MESSAGES_PER_SECOND} 即视为超限
     *
     * @param userId 用户 ID
     * @return true 表示未超限，可以继续发送；false 表示已超限
     */
    public boolean checkMessageRateLimit(Long userId) {
        if (userId == null) {
            return true;
        }

        try {
            String key = buildUserMessageKey(userId);
            long count = rateLimitStore.incrementRateLimit(key, MESSAGE_RATE_LIMIT_WINDOW_SECONDS);

            boolean allowed = count <= WebSocketConstants.MAX_MESSAGES_PER_SECOND;
            if (!allowed) {
                log.debug("[基础限流] 消息频率超限: userId={}, count={}, limit={}",
                        userId, count, WebSocketConstants.MAX_MESSAGES_PER_SECOND);
            }
            return allowed;
        } catch (Exception e) {
            // 限流失败时，为避免影响主流程，默认放行并记录日志
            log.error("[基础限流] 检查消息限流失败，默认放行: userId={}", userId, e);
            return true;
        }
    }

    /**
     * 重置用户的消息频率计数。
     *
     * 通常在连接关闭或会话清理时调用，用于主动释放相关 Redis 限流数据。
     *
     * @param userId 用户 ID
     */
    public void resetUserMessageRate(Long userId) {
        if (userId == null) {
            return;
        }

        try {
            String key = buildUserMessageKey(userId);
            rateLimitStore.resetRateLimit(key);
            log.debug("[基础限流] 重置用户消息频率计数: userId={}", userId);
        } catch (Exception e) {
            log.error("[基础限流] 重置用户消息频率计数失败: userId={}", userId, e);
        }
    }

    private String buildUserMessageKey(Long userId) {
        return MESSAGE_RATE_LIMIT_USER_KEY_PREFIX + userId;
    }
}

