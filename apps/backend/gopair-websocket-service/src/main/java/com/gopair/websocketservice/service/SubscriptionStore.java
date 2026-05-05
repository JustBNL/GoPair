package com.gopair.websocketservice.service;

import com.gopair.websocketservice.domain.SubscriptionData;

import java.util.Map;
import java.util.Optional;

/**
 * 订阅与权限相关的持久化接口。
 *
 * 抽象出用户订阅数据和权限缓存的数据访问能力，
 * 便于业务层在不关心底层 Redis 细节的情况下完成读写。
 */
public interface SubscriptionStore {

    void saveUserSubscription(Long userId, String channel, SubscriptionData subscriptionData);

    void removeUserSubscription(Long userId, String channel);

    Map<String, SubscriptionData> getUserSubscriptions(Long userId);

    void batchSaveUserSubscriptions(Long userId, Map<String, SubscriptionData> subscriptions);

    void cacheUserPermission(Long userId, String resource, boolean hasPermission);

    Optional<Boolean> getCachedUserPermission(Long userId, String resource);

    void clearUserPermissionCache(Long userId);
}

