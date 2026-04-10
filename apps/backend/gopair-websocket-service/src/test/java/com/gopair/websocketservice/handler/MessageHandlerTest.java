package com.gopair.websocketservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.websocketservice.config.TestConfig;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import com.gopair.websocketservice.service.ChannelMessageRouter;
import com.gopair.websocketservice.service.ConnectionManagerService;
import com.gopair.websocketservice.service.SubscriptionManagerService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MessageHandler 单元测试。
 *
 * * [核心策略]
 * - @MockBean 所有依赖，直接 mock 调用行为。
 * - handleChannelMessage：channel_message 类型消息委托 ChannelMessageRouter 处理。
 *
 * * [覆盖场景]
 * - Happy Path：subscribe / unsubscribe / heartbeat / channel_message 路由。
 * - Negative Path：无效 JSON、未知消息类型。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
public class MessageHandlerTest {

    @Autowired
    private MessageHandler messageHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SubscriptionManagerService subscriptionManager;

    @MockBean
    private ChannelMessageRouter channelMessageRouter;

    @MockBean
    private ConnectionManagerService connectionManager;

    private WebSocketSession mockSession;
    private String testSessionId;

    @BeforeEach
    void setUp() {
        testSessionId = "msg-handler-test-session-" + System.currentTimeMillis();
        mockSession = mock(WebSocketSession.class);
        when(mockSession.getId()).thenReturn(testSessionId);
        when(mockSession.isOpen()).thenReturn(true);
    }

    // ==================== Happy Path ====================

    @Nested
    @DisplayName("Happy Path：消息类型路由")
    class MessageRoutingTests {

        @Test
        @DisplayName("subscribe 消息委托 SubscriptionManagerService 订阅")
        void testSubscribeMessageRouting() throws Exception {
            when(subscriptionManager.subscribeChannel(anyString(), any(), anyString(), any(), any()))
                    .thenReturn(true);

            String json = "{" +
                    "\"type\": \"subscribe\"," +
                    "\"eventType\": \"subscribe\"," +
                    "\"payload\": {" +
                    "\"userId\": 12345," +
                    "\"channel\": \"user:12345\"," +
                    "\"eventTypes\": [\"message\",\"typing\"]" +
                    "}" +
                    "}";

            boolean result = messageHandler.handleTextMessage(mockSession, new TextMessage(json));

            assertTrue(result);
            verify(subscriptionManager, times(1)).subscribeChannel(
                    eq(testSessionId), eq(12345L), eq("user:12345"), any(), any());
        }

        @Test
        @DisplayName("unsubscribe 消息委托 SubscriptionManagerService 取消订阅")
        void testUnsubscribeMessageRouting() throws Exception {
            when(subscriptionManager.unsubscribeChannel(anyString(), any(), anyString()))
                    .thenReturn(true);

            String json = "{" +
                    "\"type\": \"subscribe\"," +
                    "\"eventType\": \"unsubscribe\"," +
                    "\"payload\": {" +
                    "\"userId\": 12345," +
                    "\"channel\": \"user:12345\"" +
                    "}" +
                    "}";

            boolean result = messageHandler.handleTextMessage(mockSession, new TextMessage(json));

            assertTrue(result);
            verify(subscriptionManager, times(1)).unsubscribeChannel(
                    eq(testSessionId), eq(12345L), eq("user:12345"));
        }

        @Test
        @DisplayName("heartbeat 消息更新心跳并回复 pong")
        void testHeartbeatMessageUpdatesHeartbeatAndResponds() throws Exception {
            doNothing().when(connectionManager).updateHeartbeat(anyString());
            doNothing().when(mockSession).sendMessage(any(TextMessage.class));

            String json = "{" +
                    "\"type\": \"heartbeat\"," +
                    "\"eventType\": \"heartbeat\"," +
                    "\"messageId\": \"hb-001\"" +
                    "}";

            boolean result = messageHandler.handleTextMessage(mockSession, new TextMessage(json));

            assertTrue(result);
            verify(connectionManager, times(1)).updateHeartbeat(testSessionId);
            verify(mockSession, timeout(500).times(1)).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("channel_message 消息委托 ChannelMessageRouter 处理")
        void testChannelMessageRouting() throws Exception {
            doNothing().when(channelMessageRouter).processChannelMessage(any(UnifiedWebSocketMessage.class));

            // 注意：type 必须为 "channel_message" 才会路由到 handleChannelMessage
            String json = "{" +
                    "\"type\": \"channel_message\"," +
                    "\"eventType\": \"message\"," +
                    "\"channel\": \"room:chat:8888\"," +
                    "\"payload\": {" +
                    "\"content\": \"Hello\"" +
                    "}" +
                    "}";

            boolean result = messageHandler.handleTextMessage(mockSession, new TextMessage(json));

            assertTrue(result);
            verify(channelMessageRouter, times(1)).processChannelMessage(any(UnifiedWebSocketMessage.class));
        }
    }

    // ==================== Happy Path：payload 格式兼容 ====================

    @Nested
    @DisplayName("Happy Path：payload 格式兼容")
    class PayloadCompatibilityTests {

        @Test
        @DisplayName("data.payload 嵌套层级时正常解析订阅")
        void testNestedPayloadParsing() throws Exception {
            when(subscriptionManager.subscribeChannel(anyString(), any(), anyString(), any(), any()))
                    .thenReturn(true);

            String json = "{" +
                    "\"type\": \"subscribe\"," +
                    "\"eventType\": \"subscribe\"," +
                    "\"data\": {" +
                    "\"payload\": {" +
                    "\"userId\": 99999," +
                    "\"channel\": \"user:99999\"," +
                    "\"eventTypes\": [\"message\"]" +
                    "}" +
                    "}" +
                    "}";

            boolean result = messageHandler.handleTextMessage(mockSession, new TextMessage(json));

            assertTrue(result);
            verify(subscriptionManager).subscribeChannel(
                    eq(testSessionId), eq(99999L), eq("user:99999"), any(), any());
        }

        @Test
        @DisplayName("带 ISO 8601 时间戳的消息正常解析")
        void testTimestampParsing() throws Exception {
            doNothing().when(connectionManager).updateHeartbeat(anyString());
            doNothing().when(mockSession).sendMessage(any(TextMessage.class));

            String json = "{" +
                    "\"type\": \"heartbeat\"," +
                    "\"eventType\": \"heartbeat\"," +
                    "\"messageId\": \"hb-ts-001\"," +
                    "\"timestamp\": \"2026-03-15T12:30:00Z\"" +
                    "}";

            boolean result = messageHandler.handleTextMessage(mockSession, new TextMessage(json));

            assertTrue(result);
        }
    }

    // ==================== Negative Path ====================

    @Nested
    @DisplayName("Negative Path：异常场景处理")
    class NegativePathTests {

        @Test
        @DisplayName("无效 JSON 返回 false，不抛异常")
        void testInvalidJsonReturnsFalse() {
            String invalidJson = "{ invalid json }";

            boolean result = messageHandler.handleTextMessage(mockSession, new TextMessage(invalidJson));

            assertFalse(result);
        }

        @Test
        @DisplayName("未知消息类型（parseMessageType 抛异常）返回 false")
        void testUnknownMessageTypeReturnsTrue() throws Exception {
            doNothing().when(mockSession).sendMessage(any(TextMessage.class));

            String json = "{" +
                    "\"type\": \"unknown_type\"," +
                    "\"eventType\": \"unknown\"" +
                    "}";

            boolean result = messageHandler.handleTextMessage(mockSession, new TextMessage(json));

            assertFalse(result);
        }

        @Test
        @DisplayName("channel 缺失时返回 false")
        void testSubscribeWithMissingChannelReturnsFalse() throws Exception {
            String json = "{" +
                    "\"type\": \"subscribe\"," +
                    "\"eventType\": \"subscribe\"," +
                    "\"payload\": {" +
                    "\"userId\": 111" +
                    "}" +
                    "}";

            boolean result = messageHandler.handleTextMessage(mockSession, new TextMessage(json));

            assertFalse(result);
        }

        @Test
        @DisplayName("userId 缺失时返回 false（路由层捕获异常）")
        void testSubscribeWithMissingUserIdReturnsTrue() throws Exception {
            String json = "{" +
                    "\"type\": \"subscribe\"," +
                    "\"eventType\": \"subscribe\"," +
                    "\"payload\": {" +
                    "\"channel\": \"user:12345\"" +
                    "}" +
                    "}";

            boolean result = messageHandler.handleTextMessage(mockSession, new TextMessage(json));

            assertFalse(result);
        }

        @Test
        @DisplayName("subscribe 返回 false 时 handleTextMessage 也返回 false")
        void testChannelMessageFailureReturnsFalse() throws Exception {
            doThrow(new RuntimeException("Unexpected"))
                    .when(channelMessageRouter).processChannelMessage(any());

            String json = "{" +
                    "\"type\": \"channel_message\"," +
                    "\"eventType\": \"message\"," +
                    "\"channel\": \"room:chat:8888\"," +
                    "\"payload\": {\"content\": \"Hi\"}" +
                    "}";

            boolean result = messageHandler.handleTextMessage(mockSession, new TextMessage(json));

            assertFalse(result);
        }

        @Test
        @DisplayName("heartbeat 中 updateHeartbeat 抛异常时返回 false")
        void testHeartbeatFailureReturnsFalse() throws Exception {
            doThrow(new RuntimeException("Redis error"))
                    .when(connectionManager).updateHeartbeat(anyString());

            String json = "{" +
                    "\"type\": \"heartbeat\"," +
                    "\"eventType\": \"heartbeat\"" +
                    "}";

            boolean result = messageHandler.handleTextMessage(mockSession, new TextMessage(json));

            assertFalse(result);
        }

        @Test
        @DisplayName("heartbeat 中 sendMessage 抛异常时返回 false")
        void testHeartbeatSendMessageFailureReturnsFalse() throws Exception {
            doNothing().when(connectionManager).updateHeartbeat(anyString());
            doThrow(new RuntimeException("Connection closed"))
                    .when(mockSession).sendMessage(any(TextMessage.class));

            String json = "{" +
                    "\"type\": \"heartbeat\"," +
                    "\"eventType\": \"heartbeat\"" +
                    "}";

            boolean result = messageHandler.handleTextMessage(mockSession, new TextMessage(json));

            assertFalse(result);
        }
    }
}
