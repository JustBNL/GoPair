package com.gopair.websocketservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.websocketservice.config.TestConfig;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ChannelMessageRouter 单元测试（纯 Mock 版）。
 *
 * * [核心策略]
 * - ConnectionManagerService：@MockBean，完全控制 session 查找。
 * - SubscriptionManagerService：@MockBean，完全控制订阅者查询。
 * - 不依赖任何真实内部调用链，隔离测试路由分发逻辑。
 * - processChannelMessage 为 void，通过 CompletableFuture 异步执行，
 *   内部使用 dispatchExecutor（CallerRunsPolicy），任务在调用线程执行，
 *   故 verify 可直接捕获。
 *
 * * [覆盖场景]
 * - Happy Path：单订阅者、多订阅者分发、批量消息。
 * - Edge Cases：频道无订阅者、channel 为 null、session 不存在。
 * - Negative：session 已关闭、一个 session 异常不影响其他 session。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
public class ChannelMessageRouterTest {

    @Autowired
    private ChannelMessageRouter channelMessageRouter;

    @MockBean
    private ConnectionManagerService connectionManagerService;

    @MockBean
    private SubscriptionManagerService subscriptionManagerService;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketSession mockSession1;
    private WebSocketSession mockSession2;
    private String sessionId1;
    private String sessionId2;

    @BeforeEach
    void setUp() throws Exception {
        sessionId1 = "router-session-1";
        sessionId2 = "router-session-2";

        mockSession1 = mock(WebSocketSession.class);
        when(mockSession1.getId()).thenReturn(sessionId1);
        when(mockSession1.isOpen()).thenReturn(true);
        doAnswer(inv -> null).when(mockSession1).sendMessage(any(TextMessage.class));

        mockSession2 = mock(WebSocketSession.class);
        when(mockSession2.getId()).thenReturn(sessionId2);
        when(mockSession2.isOpen()).thenReturn(true);
        doAnswer(inv -> null).when(mockSession2).sendMessage(any(TextMessage.class));
    }

    // ==================== Happy Path ====================

    @Nested
    @DisplayName("Happy Path：消息路由分发")
    class HappyPathTests {

        @Test
        @DisplayName("单订阅者路由成功，消息被发送")
        void testSingleSubscriberReceivesMessage() throws Exception {
            String channel = "system:router:1";
            when(subscriptionManagerService.getChannelSubscribers(eq(channel), eq("message")))
                    .thenReturn(Set.of(sessionId1));
            when(connectionManagerService.getSession(sessionId1)).thenReturn(mockSession1);
            when(connectionManagerService.getSessionInfo(sessionId1)).thenReturn(null);

            UnifiedWebSocketMessage message = newMessage(channel, "msg-001", "message", Map.of("text", "hello"));

            channelMessageRouter.processChannelMessage(message);

            verify(mockSession1, times(1)).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("多订阅者并行分发，消息均被发送")
        void testMultipleSubscribersReceiveMessage() throws Exception {
            String channel = "system:router:multi";
            when(subscriptionManagerService.getChannelSubscribers(eq(channel), eq("message")))
                    .thenReturn(Set.of(sessionId1, sessionId2));
            when(connectionManagerService.getSession(sessionId1)).thenReturn(mockSession1);
            when(connectionManagerService.getSession(sessionId2)).thenReturn(mockSession2);
            when(connectionManagerService.getSessionInfo(sessionId1)).thenReturn(null);
            when(connectionManagerService.getSessionInfo(sessionId2)).thenReturn(null);

            UnifiedWebSocketMessage message = newMessage(channel, "msg-002", "message", Map.of("text", "broadcast"));

            channelMessageRouter.processChannelMessage(message);

            verify(mockSession1, times(1)).sendMessage(any(TextMessage.class));
            verify(mockSession2, times(1)).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("批量消息按频道分组分发")
        void testBatchMessagesGroupedByChannel() throws Exception {
            // 频道1：两个订阅者
            when(subscriptionManagerService.getChannelSubscribers(eq("system:router:batch1"), eq("msg1")))
                    .thenReturn(Set.of(sessionId1, sessionId2));
            when(subscriptionManagerService.getChannelSubscribers(eq("system:router:batch1"), eq("msg2")))
                    .thenReturn(Set.of(sessionId1, sessionId2));
            // 频道2：一个订阅者
            when(subscriptionManagerService.getChannelSubscribers(eq("system:router:batch2"), eq("msg3")))
                    .thenReturn(Set.of(sessionId1));

            when(connectionManagerService.getSession(sessionId1)).thenReturn(mockSession1);
            when(connectionManagerService.getSession(sessionId2)).thenReturn(mockSession2);
            when(connectionManagerService.getSessionInfo(sessionId1)).thenReturn(null);
            when(connectionManagerService.getSessionInfo(sessionId2)).thenReturn(null);

            List<UnifiedWebSocketMessage> batch = List.of(
                    newMessage("system:router:batch1", "msg-batch-1", "msg1", Map.of("n", 1)),
                    newMessage("system:router:batch1", "msg-batch-2", "msg2", Map.of("n", 2)),
                    newMessage("system:router:batch2", "msg-batch-3", "msg3", Map.of("n", 3))
            );

            channelMessageRouter.processBatchMessages(batch);

            // batch1 两条消息各发给 session1 和 session2 = 4 次
            // batch2 一条消息发给 session1 = 1 次
            verify(mockSession1, times(3)).sendMessage(any(TextMessage.class));
            verify(mockSession2, times(2)).sendMessage(any(TextMessage.class));
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases：边界条件")
    class EdgeCaseTests {

        @Test
        @DisplayName("频道无订阅者，不发送消息")
        void testNoSubscribersNoMessageSent() throws Exception {
            String channel = "system:router:empty";
            when(subscriptionManagerService.getChannelSubscribers(eq(channel), eq("message")))
                    .thenReturn(Collections.emptySet());

            UnifiedWebSocketMessage message = newMessage(channel, "msg-no-sub", "message", Map.of());

            channelMessageRouter.processChannelMessage(message);

            verify(mockSession1, never()).sendMessage(any(TextMessage.class));
            verify(mockSession2, never()).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("channel 为 null 记录错误日志，不抛异常")
        void testNullChannelHandled() {
            UnifiedWebSocketMessage message = new UnifiedWebSocketMessage();
            message.setMessageId("msg-null-channel");
            message.setChannel(null);

            assertDoesNotThrow(() -> channelMessageRouter.processChannelMessage(message));
        }

        @Test
        @DisplayName("订阅者 sessionId 不存在时不发送消息")
        void testSubscriberSessionNotFound() throws Exception {
            String channel = "system:router:orphan";
            when(subscriptionManagerService.getChannelSubscribers(eq(channel), eq("message")))
                    .thenReturn(Set.of("non-existent-session"));
            when(connectionManagerService.getSession("non-existent-session")).thenReturn(null);

            UnifiedWebSocketMessage message = newMessage(channel, "msg-orphan", "message", Map.of());

            channelMessageRouter.processChannelMessage(message);

            verify(mockSession1, never()).sendMessage(any(TextMessage.class));
            verify(mockSession2, never()).sendMessage(any(TextMessage.class));
        }
    }

    // ==================== Negative Path ====================

    @Nested
    @DisplayName("Negative Path：异常处理")
    class NegativePathTests {

        @Test
        @DisplayName("session 已关闭时不发送消息，不抛异常")
        void testClosedSessionNotSent() throws Exception {
            String channel = "system:router:closed";
            when(subscriptionManagerService.getChannelSubscribers(eq(channel), eq("message")))
                    .thenReturn(Set.of(sessionId1));
            when(connectionManagerService.getSession(sessionId1)).thenReturn(mockSession1);
            when(mockSession1.isOpen()).thenReturn(false);

            UnifiedWebSocketMessage message = newMessage(channel, "msg-closed", "message", Map.of());

            assertDoesNotThrow(() -> channelMessageRouter.processChannelMessage(message));

            verify(mockSession1, never()).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("一个 session 异常不影响其他 session")
        void testOneSessionExceptionDoesNotAffectOthers() throws Exception {
            String channel = "system:router:mixed";
            when(subscriptionManagerService.getChannelSubscribers(eq(channel), eq("message")))
                    .thenReturn(Set.of(sessionId1, sessionId2));
            when(connectionManagerService.getSession(sessionId1)).thenReturn(mockSession1);
            when(connectionManagerService.getSession(sessionId2)).thenReturn(mockSession2);
            when(connectionManagerService.getSessionInfo(sessionId1)).thenReturn(null);
            when(connectionManagerService.getSessionInfo(sessionId2)).thenReturn(null);

            // session1 抛异常
            doThrow(new RuntimeException("send error"))
                    .when(mockSession1).sendMessage(any(TextMessage.class));

            UnifiedWebSocketMessage message = newMessage(channel, "msg-mixed", "message", Map.of());

            assertDoesNotThrow(() -> channelMessageRouter.processChannelMessage(message));

            // session2 仍收到消息
            verify(mockSession2, times(1)).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("批量消息中 channel 为 null 的跳过")
        void testBatchMessageWithNullChannelSkipped() throws Exception {
            when(subscriptionManagerService.getChannelSubscribers(eq("system:router:batch"), eq("message")))
                    .thenReturn(Set.of(sessionId1));
            when(connectionManagerService.getSession(sessionId1)).thenReturn(mockSession1);
            when(connectionManagerService.getSessionInfo(sessionId1)).thenReturn(null);

            List<UnifiedWebSocketMessage> batch = List.of(
                    newMessage("system:router:batch", "msg-ok", "message", Map.of("ok", true)),
                    newMessage(null, "msg-null-channel", "message", Map.of())
            );

            channelMessageRouter.processBatchMessages(batch);

            // 只有有效频道的消息被发送
            verify(mockSession1, times(1)).sendMessage(any(TextMessage.class));
        }
    }

    // ==================== Helper ====================

    private UnifiedWebSocketMessage newMessage(String channel, String messageId, String eventType, Map<String, Object> payload) {
        UnifiedWebSocketMessage message = new UnifiedWebSocketMessage();
        message.setChannel(channel);
        message.setMessageId(messageId);
        message.setEventType(eventType);
        message.setPayload(payload);
        return message;
    }
}
