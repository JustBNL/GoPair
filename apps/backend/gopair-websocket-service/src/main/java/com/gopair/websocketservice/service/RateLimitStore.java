package com.gopair.websocketservice.service;

/**
 * 频率限制相关的数据访问接口。
 *
 * 对外仅暴露限流计数的自增、查询与重置能力，
 * 上层服务无需感知具体的 Redis key 前缀与过期策略。
 */
public interface RateLimitStore {

    long incrementRateLimit(String key, long windowSeconds);

    long getRateLimitCount(String key);

    void resetRateLimit(String key);
}

