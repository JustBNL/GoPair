//package com.gopair.websocketservice;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.gopair.websocketservice.config.TestConfig;
//import com.gopair.websocketservice.domain.ChannelSubscription;
//import com.gopair.websocketservice.handler.GlobalWebSocketHandler;
//import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
//import com.gopair.websocketservice.service.*;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.*;
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.context.annotation.Import;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.web.socket.CloseStatus;
//import org.springframework.web.socket.TextMessage;
//import org.springframework.web.socket.WebSocketSession;
//
//import java.io.IOException;
//import java.util.Map;
//import java.util.Set;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
///**
// * WebSocket 全链路生命周期集成测试。
// *
// * * [核心策略]
// * - RedisOperationService / RabbitTemplate 使用 @MockBean，验证方法调用次数和参数。
// * - ConnectionManagerService / SubscriptionManagerService / BasicSubscriptionService 使用真实 Bean，验证状态流转。
// * - 全链路流编排：
// *   * 主干流 A：connect → subscribe → heartbeat → disconnect
// *   * 分支流 B-1：唯一会话断连 → 发送 offline MQ
// *   * 分支流 B-2：TTL 边界（getSession 返回空 Map）→ userId 解析失败 → 不发 offline
// *   * 分支流 C：用户信息无效连接拒绝
// *
// * * [脏数据清理]
// * - 每个测试的 @AfterEach 中清理本次创建的订阅记录。
// */
//@Slf4j
//@SpringBootTest
//@ActiveProfiles("test")
//@Import(TestConfig.class)
//@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
//public class WebSocketLifecycleIntegrationTest {
//
//    @MockBean
//    private RabbitTemplate rabbitTemplate;
//
//    @MockBean
//    private RedisOperationService redisOperationService;
//
//    @Autowired
//    private ConnectionManagerService connectionManager;
//
//    @Autowired
//    private SubscriptionManagerService subscriptionManager;
//
//    @Autowired
//    private BasicSubscriptionService basicSubscriptionService;
//
//    @Autowired
//    private BasicRateLimitService basicRateLimitService;
//
//    @Autowired
//    private GlobalWebSocketHandler globalWebSocketHandler;
//
//    private static final Long TEST_USER_ID = System.currentTimeMillis();
//    private static final Long OTHER_USER_ID = TEST_USER_ID + 1;
//
//    private WebSocketSession mockSession1;
//    private WebSocketSession mockSession2;
//
//    @BeforeEach
//    void setUp() throws Exception {
//        reset(redisOperationService);
//
//        // 所有 Redis 操作默认 no-op
//        doNothing().when(redisOperationService).saveSession(anyString(), anyLong(), anyString(), any(), anyString(), anyLong());
//        doNothing().when(redisOperationService).addUserSession(anyLong(), anyString());
//        doNothing().when(redisOperationService).removeSession(anyString());
//        doNothing().when(redisOperationService).removeUserSession(anyLong(), anyString());
//        doNothing().when(redisOperationService).refreshSessionTtl(anyString(), anyLong());
//        doNothing().when(redisOperationService).saveUserSubscription(anyLong(), anyString(), any());
//        doNothing().when(redisOperationService).removeUserSubscription(anyLong(), anyString());
//        doNothing().when(redisOperationService).batchSaveUserSubscriptions(anyLong(), any());
//        when(redisOperationService.getUserSubscriptions(anyLong())).thenReturn(java.util.Collections.emptyMap());
//
//        // 创建模拟会话 1
//        mockSession1 = mock(WebSocketSession.class);
//        String sessionId1 = "lifecycle-session-1-" + System.currentTimeMillis();
//        when(mockSession1.getId()).thenReturn(sessionId1);
//        when(mockSession1.isOpen()).thenReturn(true);
//        stubSendMessage(mockSession1);
//
//        // 创建模拟会话 2
//        mockSession2 = mock(WebSocketSession.class);
//        String sessionId2 = "lifecycle-session-2-" + System.currentTimeMillis();
//        when(mockSession2.getId()).thenReturn(sessionId2);
//        when(mockSession2.isOpen()).thenReturn(true);
//        stubSendMessage(mockSession2);
//    }
//
//    private void stubSendMessage(WebSocketSession session) {
//        try {
//            doNothing().when(session).sendMessage(any(TextMessage.class));
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    @AfterEach
//    void tearDown() {
//        Set<String> channelsToClean = Set.of(
//                "user:" + TEST_USER_ID,
//                "system:global",
//                "room:chat:888",
//                "room:chat:999"
//        );
//        subscriptionManager.cleanupUserSubscriptions(TEST_USER_ID, channelsToClean);
//        subscriptionManager.cleanupUserSubscriptions(OTHER_USER_ID, channelsToClean);
//    }
//
//    // ==================== 主干流 A：connect → subscribe → heartbeat → disconnect ====================
//
//    @Nested
//    @DisplayName("主干流 A：正常连接→订阅→心跳→断连")
//    class HappyPathMainFlow {
//
//        @Test
//        @Order(1)
//        @DisplayName("Step 1 [connect] 基础订阅完成后 sessions、Redis、内存状态均正确")
//        void testConnectEstablishesSessionAndSubscriptions() {
//            // given: getSession 返回用户会话数据（getSessionId 从 sessions Map 中取）
//            // 注意：addGlobalSession 只写 sessions Map，不依赖 getSession
//            // 因此只需验证 addGlobalSession 被调用
//
//            // when: 触发基础订阅
//            basicSubscriptionService.performLoginBasicSubscription(mockSession1.getId(), TEST_USER_ID);
//
//            // then Step 1: 基础订阅包含 user + system 两个频道
//            Set<ChannelSubscription> subs = subscriptionManager.getUserSubscriptions(TEST_USER_ID);
//            assertFalse(subs.isEmpty(), "登录后应有基础订阅");
//            assertTrue(subs.stream().anyMatch(s -> s.getChannel().equals("user:" + TEST_USER_ID)),
//                    "应订阅个人频道 user:" + TEST_USER_ID);
//            assertTrue(subs.stream().anyMatch(s -> s.getChannel().equals("system:global")),
//                    "应订阅系统全局频道");
//
//            // then Step 1: Redis saveUserSubscription 被调用
//            verify(redisOperationService, atLeastOnce()).saveUserSubscription(eq(TEST_USER_ID), anyString(), any(Map.class));
//
//            log.info("==== [Step 1 connect] 状态校验 ====");
//            log.info("sessions Map 大小: {}", connectionManager.getActiveSessionCount());
//            log.info("用户订阅数: {}, channels: {}",
//                    subs.size(), subs.stream().map(ChannelSubscription::getChannel).toList());
//        }
//
//        @Test
//        @Order(2)
//        @DisplayName("Step 2 [subscribe] 手动订阅 room 频道，Redis 和内存均正确")
//        void testManualSubscribePersistsToRedis() {
//            // given: 登录后已建立基础订阅（前置测试保证）
//            String roomChannel = "room:chat:888";
//            Set<String> eventTypes = Set.of("message", "typing");
//
//            // when: 用户主动订阅 room:chat:888
//            boolean subscribeResult = subscriptionManager.subscribeChannel(
//                    mockSession1.getId(), TEST_USER_ID, roomChannel, eventTypes, "manual");
//
//            // then Step 2: 订阅成功
//            assertTrue(subscribeResult, "手动订阅应成功");
//
//            // then Step 2: Redis saveUserSubscription 被调用
//            verify(redisOperationService, atLeastOnce()).saveUserSubscription(
//                    eq(TEST_USER_ID), eq(roomChannel), any(Map.class));
//
//            // then Step 2: 内存中订阅记录存在
//            Set<ChannelSubscription> subs = subscriptionManager.getUserSubscriptions(TEST_USER_ID);
//            assertTrue(subs.stream().anyMatch(s -> roomChannel.equals(s.getChannel())),
//                    "room:chat:888 应在用户订阅列表中");
//
//            // then Step 2: 频道订阅者列表包含 session
//            Set<String> channelSubscribers = subscriptionManager.getChannelSubscribers(roomChannel, "message");
//            assertTrue(channelSubscribers.contains(mockSession1.getId()),
//                    "session 应在频道订阅者列表中");
//
//            log.info("==== [Step 2 subscribe] 状态校验 ====");
//            log.info("用户当前订阅数: {}, channels: {}",
//                    subs.size(), subs.stream().map(ChannelSubscription::getChannel).toList());
//        }
//
//        @Test
//        @Order(3)
//        @DisplayName("Step 3 [heartbeat] 心跳更新 Redis TTL")
//        void testHeartbeatRefreshesRedisTtl() {
//            // given: getSession 返回会话数据（从 sessions Map 中读）
//            // updateHeartbeat 调用 sessionStore.refreshSessionTtl(sessionId, SESSION_TTL_SECONDS)
//            // 不依赖 getSession 返回值
//
//            // when: 发送心跳
//            connectionManager.updateHeartbeat(mockSession1.getId());
//
//            // then Step 3: Redis refreshSessionTtl 被调用
//            verify(redisOperationService, times(1))
//                    .refreshSessionTtl(eq(mockSession1.getId()), anyLong());
//
//            log.info("==== [Step 3 heartbeat] 状态校验 ====");
//            log.info("Redis refreshSessionTtl 调用: 1 次");
//        }
//
//        @Test
//        @Order(4)
//        @DisplayName("Step 4 [disconnect] sessions Map 清空、Redis 清理")
//        void testDisconnectClearsAllResources() throws Exception {
//            // given: getSession 返回会话数据（含 userId）
//            when(redisOperationService.getSession(mockSession1.getId()))
//                    .thenReturn(Map.of("userId", TEST_USER_ID.toString()));
//            when(redisOperationService.getUserSessions(TEST_USER_ID))
//                    .thenReturn(java.util.Collections.emptySet());
//
//            // when: afterConnectionClosed
//            globalWebSocketHandler.afterConnectionClosed(mockSession1, CloseStatus.NORMAL);
//
//            // then Step 4: Redis removeSession 被调用
//            verify(redisOperationService, times(1)).removeSession(mockSession1.getId());
//
//            // then Step 4: Redis removeUserSession 被调用
//            verify(redisOperationService, times(1)).removeUserSession(TEST_USER_ID, mockSession1.getId());
//
//            // then Step 4: sessions Map 已清空
//            assertNull(connectionManager.getSession(mockSession1.getId()),
//                    "断连后会话应从 sessions Map 移除");
//
//            // then Step 4: RabbitMQ system.offline 消息发送
//            verify(rabbitTemplate, times(1)).convertAndSend(
//                    anyString(), eq("system.offline"), any(UnifiedWebSocketMessage.class));
//
//            log.info("==== [Step 4 disconnect] 状态校验 ====");
//            log.info("Redis removeSession 调用: 1 次");
//            log.info("RabbitMQ offline 消息发送: 1 次");
//            log.info("sessions Map 大小: {}", connectionManager.getActiveSessionCount());
//        }
//    }
//
//    // ==================== 分支流 B-2：TTL 边界场景 ====================
//
//    @Nested
//    @DisplayName("分支流 B-2：Redis TTL 边界场景（getSession 返回空）")
//    class RedisTtlEdgeCaseTests {
//
//        @Test
//        @DisplayName("getSession 返回空 Map：userId 为 null，不发 offline MQ")
//        void testEmptySessionGetUserIdFailsNoOfflineMq() throws Exception {
//            // given: getSession 返回空（TTL 刚好过期场景）
//            when(redisOperationService.getSession(anyString()))
//                    .thenReturn(java.util.Collections.emptyMap());
//
//            // when: afterConnectionClosed
//            globalWebSocketHandler.afterConnectionClosed(mockSession1, CloseStatus.GOING_AWAY);
//
//            // then: userId 为 null，removeUserSession 不被调用（因为 userId 为 null）
//            verify(redisOperationService, never()).removeUserSession(anyLong(), anyString());
//
//            // then: 不发送 offline MQ（userId 为 null）
//            verify(rabbitTemplate, never())
//                    .convertAndSend(anyString(), eq("system.offline"), any(UnifiedWebSocketMessage.class));
//
//            log.info("==== [分支流 B-2] TTL 边界场景状态校验 ====");
//            log.info("getSession 返回空，userId 为 null，无 offline MQ 发送");
//        }
//    }
//
//    // ==================== 分支流 C：无效用户信息连接拒绝 ====================
//
//    @Nested
//    @DisplayName("分支流 C：无效用户信息连接拒绝")
//    class InvalidConnectionFlowTests {
//
//        @Test
//        @DisplayName("无效连接断连：getSession 返回空，getUserSessions 返回 null，视为无会话，发送 offline MQ")
//        void testInvalidConnectionDisconnectSendsOfflineWhenNoSessions() throws Exception {
//            // given: session 数据存在但 getUserSessions 返回 null（视为无会话）
//            String sessionId = "invalid-session-" + System.currentTimeMillis();
//            when(mockSession1.getId()).thenReturn(sessionId);
//            when(redisOperationService.getSession(sessionId))
//                    .thenReturn(Map.of("userId", OTHER_USER_ID.toString()));
//            when(redisOperationService.getUserSessions(OTHER_USER_ID)).thenReturn(null);
//
//            // when: afterConnectionClosed
//            globalWebSocketHandler.afterConnectionClosed(mockSession1, CloseStatus.GOING_AWAY);
//
//            // then: null 视为无会话，发送 offline MQ
//            verify(rabbitTemplate, times(1)).convertAndSend(
//                    anyString(), eq("system.offline"), any(UnifiedWebSocketMessage.class));
//
//            log.info("==== [分支流 C] 无效连接清理状态校验 ====");
//            log.info("getUserSessions 返回 null，视为无会话，offline MQ 已发送");
//        }
//    }
//
//    // ==================== 全链路状态汇总日志 ====================
//
//    @Test
//    @DisplayName("全链路状态汇总：sessions、subscriptions、Redis 操作次数")
//    void testFullLifecycleStatsSummary() {
//        log.info("==== [全链路状态汇总] ====");
//        log.info("sessions Map 当前大小: {}", connectionManager.getActiveSessionCount());
//        log.info("连接统计: {}", connectionManager.getConnectionStats());
//
//        Map<String, Object> stats = subscriptionManager.getSubscriptionStats();
//        log.info("订阅统计: totalUsers={}, totalChannels={}, totalSubscriptions={}",
//                stats.get("totalUsers"), stats.get("totalChannels"), stats.get("totalSubscriptions"));
//    }
//}
