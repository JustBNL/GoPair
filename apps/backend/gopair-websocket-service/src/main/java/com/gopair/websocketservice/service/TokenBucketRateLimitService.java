package com.gopair.websocketservice.service;

import com.gopair.websocketservice.constants.WebSocketConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 令牌桶限流服务
 *
 * <p>使用 Redis + Lua 脚本实现分布式令牌桶算法，保证操作的原子性。
 *
 * <p>算法说明：
 * <ul>
 *   <li>每个用户拥有独立的令牌桶，存储于 Redis Hash 中。</li>
 *   <li>桶容量为 {@link WebSocketConstants#TOKEN_BUCKET_CAPACITY}，即允许的最大突发消息数。</li>
 *   <li>令牌以 {@link WebSocketConstants#TOKEN_BUCKET_REFILL_RATE} 个/秒的速率持续补充。</li>
 *   <li>每次发送消息时，根据消息类型消耗对应数量的令牌。</li>
 *   <li>令牌不足时拒绝发送，避免了固定窗口算法的边界突刺问题。</li>
 * </ul>
 *
 * <p>消息类型令牌消耗：
 * <ul>
 *   <li>TEXT (1)   — 消耗 1 个令牌</li>
 *   <li>IMAGE (2)  — 消耗 2 个令牌</li>
 *   <li>FILE (3)   — 消耗 3 个令牌</li>
 *   <li>VOICE (4)  — 消耗 2 个令牌</li>
 *   <li>EMOJI (5)  — 消耗 1 个令牌</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBucketRateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 令牌桶 Lua 脚本。
     *
     * <p>KEYS[1] — 令牌桶 Redis Key
     * <p>ARGV[1] — 桶容量 (capacity)
     * <p>ARGV[2] — 每毫秒补充令牌数 (refillRatePerMs, 精度为小数字符串)
     * <p>ARGV[3] — 本次消耗令牌数 (cost)
     * <p>ARGV[4] — Key TTL（秒）
     * <p>ARGV[5] — 当前时间戳（毫秒）
     *
     * <p>返回值：1 表示允许，0 表示拒绝
     */
    private static final String TOKEN_BUCKET_LUA_SCRIPT =
            "local key = KEYS[1]\n" +
            "local capacity = tonumber(ARGV[1])\n" +
            "local refill_rate_per_ms = tonumber(ARGV[2])\n" +
            "local cost = tonumber(ARGV[3])\n" +
            "local ttl = tonumber(ARGV[4])\n" +
            "local now = tonumber(ARGV[5])\n" +
            "\n" +
            "local tokens = tonumber(redis.call('HGET', key, 'tokens'))\n" +
            "local last_refill = tonumber(redis.call('HGET', key, 'last_refill_time'))\n" +
            "\n" +
            "if tokens == nil then\n" +
            "  tokens = capacity\n" +
            "  last_refill = now\n" +
            "end\n" +
            "\n" +
            "local elapsed = math.max(0, now - last_refill)\n" +
            "local refilled = elapsed * refill_rate_per_ms\n" +
            "tokens = math.min(capacity, tokens + refilled)\n" +
            "\n" +
            "if tokens >= cost then\n" +
            "  tokens = tokens - cost\n" +
            "  redis.call('HSET', key, 'tokens', tokens, 'last_refill_time', now)\n" +
            "  redis.call('EXPIRE', key, ttl)\n" +
            "  return 1\n" +
            "else\n" +
            "  redis.call('HSET', key, 'tokens', tokens, 'last_refill_time', now)\n" +
            "  redis.call('EXPIRE', key, ttl)\n" +
            "  return 0\n" +
            "end\n";

    private static final DefaultRedisScript<Long> REDIS_SCRIPT;

    static {
        REDIS_SCRIPT = new DefaultRedisScript<>();
        REDIS_SCRIPT.setScriptText(TOKEN_BUCKET_LUA_SCRIPT);
        REDIS_SCRIPT.setResultType(Long.class);
    }

    /**
     * 根据消息类型检查令牌桶限流（差异化消耗）。
     *
     * @param userId      用户 ID
     * @param messageType 消息类型（1=TEXT, 2=IMAGE, 3=FILE, 4=VOICE, 5=EMOJI），为 null 时默认消耗 1
     * @return true 表示允许发送；false 表示令牌不足，需限流
     */
    public boolean checkMessageRateLimit(Long userId, Integer messageType) {
        if (userId == null) {
            return true;
        }
        int cost = resolveCost(messageType);
        return executeTokenBucket(userId, cost);
    }

    /**
     * 使用默认消耗（1 个令牌）检查限流，兼容不感知消息类型的调用方。
     *
     * @param userId 用户 ID
     * @return true 表示允许发送；false 表示令牌不足
     */
    public boolean checkMessageRateLimit(Long userId) {
        return checkMessageRateLimit(userId, null);
    }

    /**
     * 重置用户令牌桶（连接关闭时调用，释放 Redis 资源）。
     *
     * @param userId 用户 ID
     */
    public void resetBucket(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            String key = buildKey(userId);
            redisTemplate.delete(key);
            log.debug("[令牌桶] 重置用户令牌桶: userId={}", userId);
        } catch (Exception e) {
            log.error("[令牌桶] 重置令牌桶失败: userId={}", userId, e);
        }
    }

    // ------------------------------------------------------------------ private

    private boolean executeTokenBucket(Long userId, int cost) {
        try {
            String key = buildKey(userId);
            // 每毫秒补充令牌数 = refillRate / 1000，保留足够精度
            double refillRatePerMs = (double) WebSocketConstants.TOKEN_BUCKET_REFILL_RATE / 1000.0;
            long now = System.currentTimeMillis();

            List<String> keys = Collections.singletonList(key);
            Long result = redisTemplate.execute(
                    REDIS_SCRIPT,
                    keys,
                    (double) WebSocketConstants.TOKEN_BUCKET_CAPACITY,
                    refillRatePerMs,
                    (double) cost,
                    (double) WebSocketConstants.TOKEN_BUCKET_TTL_SECONDS,
                    (double) now
            );

            boolean allowed = Long.valueOf(1L).equals(result);
            if (!allowed) {
                log.debug("[令牌桶] 消息限流触发: userId={}, cost={}, capacity={}",
                        userId, cost, WebSocketConstants.TOKEN_BUCKET_CAPACITY);
            }
            return allowed;
        } catch (Exception e) {
            // Redis 故障时默认拒绝，防止攻击者利用 Redis 闪断绕过限流
            log.error("[令牌桶] 执行令牌桶脚本失败，保守拒绝: userId={}", userId, e);
            return false;
        }
    }

    /**
     * 根据消息类型解析令牌消耗数量。
     *
     * @param messageType 消息类型值
     * @return 令牌消耗数
     */
    private int resolveCost(Integer messageType) {
        if (messageType == null) {
            return 1;
        }
        return switch (messageType) {
            case 1 -> 1; // TEXT
            case 2 -> 2; // IMAGE
            case 3 -> 3; // FILE
            case 4 -> 2; // VOICE
            case 5 -> 1; // EMOJI
            default -> 1;
        };
    }

    private String buildKey(Long userId) {
        return WebSocketConstants.TOKEN_BUCKET_KEY_PREFIX + userId;
    }
}
