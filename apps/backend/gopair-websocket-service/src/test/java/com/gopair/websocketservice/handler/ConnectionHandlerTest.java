package com.gopair.websocketservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.websocketservice.config.RabbitMQConfig;
import com.gopair.websocketservice.config.TestConfig;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import com.gopair.websocketservice.service.BasicSubscriptionService;
import com.gopair.websocketservice.service.ConnectionManagerService;
import com.gopair.websocketservice.service.RedisOperationService;
import com.gopair.websocketservice.service.SubscriptionManagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ConnectionHandler 单元测试。
 *
 * * [核心策略]
 * - handleConnectionEstablished：验证用户信息 → 恢复订阅状态（含三个反向索引）→ 添加连接 → 登录基础订阅 → 发送欢迎消息。
 * - handleConnectionClosed：获取 userId 并删 Redis → 清理本地订阅内存索引 → 检查多端是否全部离线 → 发送 system.offline MQ 消息。
 * - 多端登录：任一端断开时，只有 ws:user-sessions 全部清空才发离线通知。
 *
 * * [覆盖场景]
 * - Happy Path：正常建立连接、正常断开连接（多端场景）、唯一会话断开。
 * - Negative Path：用户信息无效、Redis 无会话时（用户无其他活跃连接）发离线通知。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
public class ConnectionHandlerTest {

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private ConnectionManagerService connectionManager;

    @MockBean
    private SubscriptionManagerService subscriptionManager;

    @MockBean
    private RedisOperationService redisOperationService;

    @MockBean
    private BasicSubscriptionService basicSubscriptionService;

    @Autowired
    private ConnectionHandler connectionHandler;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketSession mockSession;
    private Long testUserId;
    private String testSessionId;
    private Map<String, Object> validUserInfo;

    @BeforeEach
    void setUp() {
        testSessionId = "conn-handler-test-session-" + System.currentTimeMillis();
        testUserId = System.currentTimeMillis();

        mockSession = mock(WebSocketSession.class);
        when(mockSession.getId()).thenReturn(testSessionId);
        when(mockSession.isOpen()).thenReturn(true);

        validUserInfo = Map.of(
                "valid", true,
                "userId", testUserId,
                "nickname", "测试用户"
        );
    }

    // ==================== Happy Path ====================

    @Nested
    @DisplayName("Happy Path：连接建立")
    class ConnectionEstablishedTests {

        @Test
        @DisplayName("成功建立连接：恢复订阅 → 添加会话 → 基础订阅 → 发送欢迎消息")
        void testHandleConnectionEstablishedSuccess() throws Exception {
            // given
            when(subscriptionManager.restoreUserSubscriptionState(eq(testUserId), eq(testSessionId))).thenReturn(0);
            doNothing().when(connectionManager).addGlobalSession(any(WebSocketSession.class), any(Long.class));
            doNothing().when(basicSubscriptionService)
                    .performLoginBasicSubscription(anyString(), any(Long.class));
            doNothing().when(mockSession).sendMessage(any(TextMessage.class));

            // when
            boolean result = connectionHandler.handleConnectionEstablished(mockSession, validUserInfo);

            // then: 返回值
            assertTrue(result, "连接建立应返回 true");

            // then: restoreUserSubscriptionState 调用参数精确验证
            verify(subscriptionManager).restoreUserSubscriptionState(testUserId, testSessionId);

            // then: addGlobalSession 调用参数精确验证
            verify(connectionManager).addGlobalSession(mockSession, testUserId);

            // then: performLoginBasicSubscription 调用参数精确验证
            verify(basicSubscriptionService).performLoginBasicSubscription(testSessionId, testUserId);

            // then: 发送欢迎消息
            verify(mockSession, timeout(500).times(1)).sendMessage(any());
        }

        @Test
        @DisplayName("欢迎消息内容包含 userId、nickname、eventType=connected")
        void testWelcomeMessageContent() throws Exception {
            // given
            when(subscriptionManager.restoreUserSubscriptionState(anyLong(), anyString())).thenReturn(0);
            doNothing().when(connectionManager).addGlobalSession(any(), anyLong());
            doNothing().when(basicSubscriptionService).performLoginBasicSubscription(anyString(), anyLong());
            TextMessage[] captured = new TextMessage[1];
            doAnswer(inv -> {
                captured[0] = (TextMessage) inv.getArgument(0);
                return null;
            }).when(mockSession).sendMessage(any(TextMessage.class));

            // when
            connectionHandler.handleConnectionEstablished(mockSession, validUserInfo);

            // then: 解析并验证欢迎消息结构
            String json = captured[0].getPayload();
            assertNotNull(json, "欢迎消息 JSON 不应为 null");

            Map<String, Object> msg = objectMapper.readValue(json, Map.class);
            assertEquals("connected", msg.get("eventType"), "eventType 应为 connected");
            assertNotNull(msg.get("messageId"), "messageId 不应为空");

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) msg.get("payload");
            assertNotNull(payload, "payload 不应为 null");
            assertEquals(testUserId, ((Number) payload.get("userId")).longValue(), "userId 应匹配");
            assertEquals("测试用户", payload.get("nickname"), "nickname 应匹配");
            assertNotNull(payload.get("connectionTime"), "connectionTime 不应为空");
        }

        @Test
        @DisplayName("handleConnectionEstablished 异常时不传播，向调用方返回 false")
        void testHandleConnectionEstablishedPropagatesExceptions() throws Exception {
            // given: restoreUserSubscriptionState 抛异常
            when(subscriptionManager.restoreUserSubscriptionState(eq(testUserId), eq(testSessionId)))
                    .thenThrow(new RuntimeException("Redis error"));

            // when
            boolean result = connectionHandler.handleConnectionEstablished(mockSession, validUserInfo);

            // then: 连接建立失败，返回 false
            assertFalse(result);
            verify(connectionManager, never()).addGlobalSession(any(), any());
            verify(basicSubscriptionService, never()).performLoginBasicSubscription(any(), any());
            verify(mockSession, never()).sendMessage(any(TextMessage.class));
        }
    }

    @Nested
    @DisplayName("Happy Path：连接断开")
    class ConnectionClosedTests {

        @Test
        @DisplayName("用户唯一会话断开，发送离线 MQ 通知，exchange 和 routingKey 均正确")
        void testLastSessionDisconnectSendsOfflineEvent() {
            // given: 这是用户唯一会话，Redis 中无其他会话
            when(connectionManager.removeSessionAndGetUserId(testSessionId)).thenReturn(testUserId);
            when(redisOperationService.getUserSessions(testUserId)).thenReturn(Set.of());

            // when
            connectionHandler.handleConnectionClosed(mockSession);

            // then: 清理本地订阅内存
            verify(subscriptionManager).cleanupSessionSubscriptions(testSessionId, testUserId);

            // then: MQ 消息的 exchange 和 routingKey 均精确验证
            verify(rabbitTemplate, times(1))
                    .convertAndSend(
                            eq(RabbitMQConfig.WEBSOCKET_EXCHANGE),
                            eq("system.offline"),
                            any(UnifiedWebSocketMessage.class));
        }

        @Test
        @DisplayName("多端登录：用户还有其他活跃会话，不发送离线 MQ 通知")
        void testMultiDeviceNoOfflineEvent() {
            // given: 用户还有其他活跃会话
            String otherSession = "other-session-" + testUserId;
            when(connectionManager.removeSessionAndGetUserId(testSessionId)).thenReturn(testUserId);
            when(redisOperationService.getUserSessions(testUserId)).thenReturn(Set.of(otherSession));

            // when
            connectionHandler.handleConnectionClosed(mockSession);

            // then: 清理本地订阅内存，但不发送离线通知
            verify(subscriptionManager).cleanupSessionSubscriptions(testSessionId, testUserId);
            verify(rabbitTemplate, never())
                    .convertAndSend(anyString(), anyString(), any(UnifiedWebSocketMessage.class));
        }

        @Test
        @DisplayName("removeSessionAndGetUserId 返回 null 时不查 Redis、不发 MQ、不清理订阅")
        void testNoUserIdNoOfflineEvent() {
            // given: 无法获取 userId
            when(connectionManager.removeSessionAndGetUserId(testSessionId)).thenReturn(null);

            // when
            connectionHandler.handleConnectionClosed(mockSession);

            // then: 不查 getUserSessions，不发 MQ，不清理订阅
            verify(subscriptionManager, never()).cleanupSessionSubscriptions(anyString(), any());
            verify(redisOperationService, never()).getUserSessions(any());
            verify(rabbitTemplate, never())
                    .convertAndSend(anyString(), anyString(), any(UnifiedWebSocketMessage.class));
        }
    }

    // ==================== Negative Path ====================

    @Nested
    @DisplayName("Negative Path：用户信息校验失败")
    class UserInfoValidationTests {

        @Test
        @DisplayName("userInfo.valid 为 false 时返回 false，不执行后续逻辑")
        void testInvalidUserInfoReturnsFalse() throws Exception {
            // given: 用户信息无效
            Map<String, Object> invalidUserInfo = Map.of(
                    "valid", false,
                    "errorReason", "Token expired"
            );

            // when
            boolean result = connectionHandler.handleConnectionEstablished(mockSession, invalidUserInfo);

            // then
            assertFalse(result, "无效用户应返回 false");
            verify(connectionManager, never()).addGlobalSession(any(), any());
            verify(basicSubscriptionService, never()).performLoginBasicSubscription(any(), any());
            verify(subscriptionManager, never()).restoreUserSubscriptionState(any(), any());
            verify(mockSession, never()).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("userId 缺失时 handleConnectionEstablished 不抛异常，返回 false")
        void testMissingUserIdReturnsFalse() {
            // given: valid=true 但缺少 userId
            Map<String, Object> missingUserIdInfo = Map.of(
                    "valid", true,
                    "nickname", "无ID用户"
            );

            // when
            boolean result = connectionHandler.handleConnectionEstablished(mockSession, missingUserIdInfo);

            // then
            assertFalse(result, "缺少 userId 应返回 false");
        }

        @Test
        @DisplayName("handleConnectionClosed 在异常时吞掉异常，不向外传播")
        void testHandleConnectionClosedSwallowsException() {
            // given: removeSessionAndGetUserId 抛异常
            when(connectionManager.removeSessionAndGetUserId(testSessionId))
                    .thenThrow(new RuntimeException("Redis connection refused"));

            // when / then: 不抛异常
            assertDoesNotThrow(() -> connectionHandler.handleConnectionClosed(mockSession));
        }

        @Test
        @DisplayName("getUserSessions 返回 null 时视为无会话，发送离线 MQ")
        void testGetUserSessionsReturnsNullSendsOffline() {
            // given: getUserSessions 返回 null
            when(connectionManager.removeSessionAndGetUserId(testSessionId)).thenReturn(testUserId);
            when(redisOperationService.getUserSessions(testUserId)).thenReturn(null);

            // when
            connectionHandler.handleConnectionClosed(mockSession);

            // then: null 视为空，发送离线通知
            verify(rabbitTemplate, times(1))
                    .convertAndSend(anyString(), eq("system.offline"), any(UnifiedWebSocketMessage.class));
        }
    }
}
