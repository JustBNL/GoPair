//package com.gopair.websocketservice;
//
//import com.gopair.websocketservice.config.TestConfig;
//import com.gopair.websocketservice.domain.ChannelSubscription;
//import com.gopair.websocketservice.service.SubscriptionManagerService;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Nested;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.context.annotation.Import;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.web.socket.WebSocketSession;
//
//import java.util.Map;
//import java.util.Set;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.Mockito.*;
//
///**
// * WebSocket 服务集成测试（重构版）。
// *
// * * [测试策略]
// * - 使用真实 Bean（SubscriptionManagerService）验证核心业务逻辑的返回值和状态变更。
// * - 对下游依赖（RedisOperationService）使用 Mock，验证方法调用次数和参数。
// * - 使用 @Nested 分组：Happy Path / Negative Path / 并发安全。
// */
//@SpringBootTest
//@ActiveProfiles("test")
//@Import(TestConfig.class)
//public class WebSocketServiceTest {
//
//    // 下游依赖使用 Mock，验证调用链
//    @MockBean
//    private com.gopair.websocketservice.service.RedisOperationService redisOperationService;
//
//    // 使用真实 Bean，验证核心业务逻辑
//    @Autowired
//    private SubscriptionManagerService subscriptionManager;
//
//    private Long testUserId;
//
//    @BeforeEach
//    void setUp() {
//        testUserId = System.currentTimeMillis();
//        // 清理上一个测试的状态
//        subscriptionManager.cleanupUserSubscriptions(testUserId, Set.of(
//                "room:chat:123", "room:file:456", "room:cleanup:test",
//                "room:chat:stat1", "room:file:stat2",
//                "user:" + testUserId, "system:global",
//                "room:chat:cleanup1", "room:chat:cleanup2"));
//    }
//
//    // ==================== Happy Path：订阅与取消订阅 ====================
//
//    @Nested
//    @DisplayName("Happy Path：订阅与取消订阅")
//    class SubscriptionTests {
//
//        @Test
//        @DisplayName("成功订阅房间频道，验证返回值和内存状态")
//        void testSubscribeRoomChannel() {
//            String channel = "room:chat:123";
//            Set<String> eventTypes = Set.of("message", "typing");
//
//            boolean result = subscriptionManager.subscribeChannel(
//                    "session-123", testUserId, channel, eventTypes, "manual");
//
//            assertTrue(result, "订阅应成功");
//
//            Set<ChannelSubscription> userSubs = subscriptionManager.getUserSubscriptions(testUserId);
//            assertEquals(1, userSubs.size(), "用户应有 1 个订阅");
//
//            ChannelSubscription subscription = userSubs.iterator().next();
//            assertEquals(channel, subscription.getChannel());
//            assertEquals(eventTypes, subscription.getEventTypes());
//            assertEquals("manual", subscription.getSource());
//
//            Set<String> subscribers = subscriptionManager.getChannelSubscribers(channel, "message");
//            assertTrue(subscribers.contains("session-123"), "session 应在频道订阅者列表中");
//        }
//
//        @Test
//        @DisplayName("成功订阅用户个人频道")
//        void testSubscribeUserChannel() {
//            String channel = "user:" + testUserId;
//            Set<String> eventTypes = Set.of("message");
//
//            boolean result = subscriptionManager.subscribeChannel(
//                    "session-user", testUserId, channel, eventTypes, "manual");
//
//            assertTrue(result);
//            boolean found = subscriptionManager.getUserSubscriptions(testUserId).stream()
//                    .anyMatch(s -> channel.equals(s.getChannel()));
//            assertTrue(found);
//        }
//
//        @Test
//        @DisplayName("成功订阅系统频道")
//        void testSubscribeSystemChannel() {
//            String channel = "system:global";
//            Set<String> eventTypes = Set.of("broadcast");
//
//            boolean result = subscriptionManager.subscribeChannel(
//                    "session-sys", testUserId, channel, eventTypes, "manual");
//
//            assertTrue(result);
//        }
//
//        @Test
//        @DisplayName("取消订阅后用户订阅列表清空，但 session 仍可被 getChannelSubscribers 返回（因订阅记录被清除时 channelSessions 同步清理）")
//        void testUnsubscribeRemovesFromUserSubscriptions() {
//            String channel = "room:file:456";
//            Set<String> eventTypes = Set.of("upload", "download");
//            subscriptionManager.subscribeChannel(
//                    "session-unsub", testUserId, channel, eventTypes, "manual");
//            assertEquals(1, subscriptionManager.getUserSubscriptions(testUserId).size());
//
//            boolean unsubResult = subscriptionManager.unsubscribeChannel("session-unsub", testUserId, channel);
//
//            assertTrue(unsubResult, "取消订阅应返回 true");
//            assertEquals(0, subscriptionManager.getUserSubscriptions(testUserId).size(),
//                    "用户订阅应被清空");
//
//            // 注意：由于 unsubscribeChannel 同步清理了 channelSessions，此处 session 已不在列表中
//            Set<String> subscribers = subscriptionManager.getChannelSubscribers(channel, "upload");
//            assertFalse(subscribers.contains("session-unsub"),
//                    "session 应从频道订阅者列表移除");
//        }
//    }
//
//    // ==================== Happy Path：会话清理 ====================
//
//    @Nested
//    @DisplayName("Happy Path：会话清理")
//    class SessionCleanupTests {
//
//        @Test
//        @DisplayName("cleanupSessionSubscriptions 清理 userSubscriptions")
//        void testCleanupSessionSubscriptions() {
//            String channel1 = "system:cleanup:1";
//            String channel2 = "system:cleanup:2";
//            String sessionId = "session-cleanup-" + testUserId;
//
//            // 记录订阅前的基线
//            int beforeCount = subscriptionManager.getUserSubscriptions(testUserId).size();
//
//            boolean r1 = subscriptionManager.subscribeChannel(
//                    sessionId, testUserId, channel1, Set.of("message"), "test");
//            boolean r2 = subscriptionManager.subscribeChannel(
//                    sessionId, testUserId, channel2, Set.of("message"), "test");
//            assertTrue(r1 && r2, "system: 前缀的 channel 应通过权限验证");
//
//            // then: 订阅后数量应增加
//            int afterCount = subscriptionManager.getUserSubscriptions(testUserId).size();
//            assertEquals(beforeCount + 2, afterCount,
//                    "订阅后应有 2 个新增订阅记录");
//
//            // when: 清理会话订阅
//            subscriptionManager.cleanupSessionSubscriptions(sessionId, testUserId);
//
//            // then: userSubscriptions 恢复基线
//            int afterCleanupCount = subscriptionManager.getUserSubscriptions(testUserId).size();
//            assertEquals(beforeCount, afterCleanupCount,
//                    "清理后应恢复到基线订阅数");
//        }
//    }
//
//    // ==================== Happy Path：订阅统计 ====================
//
//    @Nested
//    @DisplayName("Happy Path：订阅统计")
//    class StatsTests {
//
//        @Test
//        @DisplayName("getSubscriptionStats 返回正确的统计维度")
//        void testGetSubscriptionStats() {
//            subscriptionManager.subscribeChannel(
//                    "session-stat1", testUserId, "room:chat:stat1", Set.of("message"), "test");
//            subscriptionManager.subscribeChannel(
//                    "session-stat2", testUserId, "room:file:stat2", Set.of("message"), "test");
//            subscriptionManager.subscribeChannel(
//                    "session-stat3", testUserId, "user:" + testUserId, Set.of("message"), "test");
//
//            Map<String, Object> stats = subscriptionManager.getSubscriptionStats();
//
//            assertNotNull(stats);
//            assertTrue(stats.containsKey("totalSubscriptions"));
//            assertTrue(stats.containsKey("totalUsers"));
//            assertTrue(stats.containsKey("totalChannels"));
//
//            Number totalSubs = (Number) stats.get("totalSubscriptions");
//            assertTrue(totalSubs.intValue() >= 3, "总订阅数应 >= 3");
//        }
//    }
//
//    // ==================== Negative Path：权限拒绝与边界条件 ====================
//
//    @Nested
//    @DisplayName("Negative Path：权限拒绝与边界条件")
//    class NegativePathTests {
//
//        @Test
//        @DisplayName("用户不能订阅他人的 user 频道")
//        void testCannotSubscribeToOtherUserChannel() {
//            String channel = "user:" + (testUserId + 9999);
//            Set<String> eventTypes = Set.of("message");
//
//            boolean result = subscriptionManager.subscribeChannel(
//                    "session-other", testUserId, channel, eventTypes, "manual");
//
//            assertFalse(result, "他人 user 频道应拒绝订阅");
//            assertTrue(subscriptionManager.getUserSubscriptions(testUserId).isEmpty(),
//                    "不应产生订阅记录");
//        }
//
//        @Test
//        @DisplayName("未知频道类型拒绝订阅")
//        void testUnknownChannelTypeRejected() {
//            String channel = "invalid:prefix:123";
//            Set<String> eventTypes = Set.of("message");
//
//            boolean result = subscriptionManager.subscribeChannel(
//                    "session-invalid", testUserId, channel, eventTypes, "manual");
//
//            assertFalse(result, "未知前缀频道应拒绝");
//        }
//
//        @Test
//        @DisplayName("重复订阅同一频道不产生重复条目（幂等）")
//        void testDuplicateSubscriptionIsIdempotent() {
//            String channel = "system:global";
//            Set<String> eventTypes = Set.of("message");
//
//            subscriptionManager.subscribeChannel(
//                    "session-dup", testUserId, channel, eventTypes, "manual");
//            subscriptionManager.subscribeChannel(
//                    "session-dup", testUserId, channel, eventTypes, "manual");
//
//            long count = subscriptionManager.getUserSubscriptions(testUserId).stream()
//                    .filter(s -> channel.equals(s.getChannel()))
//                    .count();
//            assertEquals(1, count, "重复订阅不应产生重复条目");
//        }
//
//        @Test
//        @DisplayName("取消不存在的订阅返回 true（幂等）")
//        void testUnsubscribeNonExistentIsIdempotent() {
//            boolean result = subscriptionManager.unsubscribeChannel(
//                    "non-existent-session", testUserId, "non-existent-channel");
//            assertTrue(result, "取消不存在订阅应幂等返回 true");
//        }
//
//        @Test
//        @DisplayName("getUserSubscriptions 对不存在用户返回空集合")
//        void testGetSubscriptionsForNonExistentUser() {
//            Set<ChannelSubscription> subs = subscriptionManager.getUserSubscriptions(999999L);
//            assertTrue(subs.isEmpty());
//        }
//
//        @Test
//        @DisplayName("getChannelSubscribers 对不存在的频道返回空集合")
//        void testGetChannelSubscribersNonExistentChannel() {
//            Set<String> subscribers = subscriptionManager.getChannelSubscribers(
//                    "non-existent:channel", "message");
//            assertTrue(subscribers.isEmpty());
//        }
//    }
//
//    // ==================== Redis 集成验证 ====================
//
//    @Nested
//    @DisplayName("Redis 调用链验证")
//    class RedisIntegrationTests {
//
//        @Test
//        @DisplayName("订阅时调用 SubscriptionStore.saveUserSubscription")
//        void testSubscribeCallsSaveUserSubscription() {
//            reset(redisOperationService);
//
//            subscriptionManager.subscribeChannel(
//                    "session-redis", testUserId, "user:" + testUserId, Set.of("message"), "test");
//
//            verify(redisOperationService, times(1))
//                    .saveUserSubscription(eq(testUserId), eq("user:" + testUserId), any(Map.class));
//        }
//
//        @Test
//        @DisplayName("取消订阅时调用 SubscriptionStore.removeUserSubscription")
//        void testUnsubscribeCallsRemoveUserSubscription() {
//            String channel = "user:" + testUserId;
//            subscriptionManager.subscribeChannel(
//                    "session-rem", testUserId, channel, Set.of("message"), "test");
//            reset(redisOperationService);
//
//            subscriptionManager.unsubscribeChannel("session-rem", testUserId, channel);
//
//            verify(redisOperationService, times(1))
//                    .removeUserSubscription(testUserId, channel);
//        }
//    }
//}
