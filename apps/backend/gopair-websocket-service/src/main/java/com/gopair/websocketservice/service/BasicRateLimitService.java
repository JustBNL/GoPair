package com.gopair.websocketservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 基础频率限制服务
 *
 * <p>作为限流门面，委托 {@link TokenBucketRateLimitService} 执行基于令牌桶算法的限流检查。
 * 令牌桶算法相比原有固定窗口计数方案，可有效消除窗口边界的突刺问题，同时允许合理的短时突发。
 *
 * <p>对外保持原有方法签名不变，以降低调用方的改动成本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BasicRateLimitService {

    private final TokenBucketRateLimitService tokenBucketRateLimitService;

    /**
     * 检查用户消息发送是否触发频率限制（默认消耗 1 个令牌）。
     *
     * @param userId 用户 ID
     * @return true 表示未超限，可以继续发送；false 表示已超限
     */
    public boolean checkMessageRateLimit(Long userId) {
        return tokenBucketRateLimitService.checkMessageRateLimit(userId);
    }

    /**
     * 根据消息类型检查频率限制（差异化令牌消耗）。
     *
     * @param userId      用户 ID
     * @param messageType 消息类型（1=TEXT, 2=IMAGE, 3=FILE, 4=VOICE, 5=EMOJI）
     * @return true 表示未超限；false 表示已超限
     */
    public boolean checkMessageRateLimit(Long userId, Integer messageType) {
        return tokenBucketRateLimitService.checkMessageRateLimit(userId, messageType);
    }

    /**
     * 重置用户的消息频率计数（连接关闭时调用）。
     *
     * @param userId 用户 ID
     */
    public void resetUserMessageRate(Long userId) {
        tokenBucketRateLimitService.resetBucket(userId);
    }
}
