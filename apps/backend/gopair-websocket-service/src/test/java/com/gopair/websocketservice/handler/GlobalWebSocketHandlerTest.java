package com.gopair.websocketservice.handler;

import com.gopair.websocketservice.config.TestConfig;
import com.gopair.websocketservice.enums.WebSocketErrorCode;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import com.gopair.websocketservice.service.BasicRateLimitService;
import com.gopair.websocketservice.service.BasicSubscriptionService;
import com.gopair.websocketservice.service.ChannelMessageRouter;
import com.gopair.websocketservice.service.ConnectionManagerService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GlobalWebSocketHandler 单元测试。
 *
 * * [核心策略]
 * - 全局入口处理器，协调 ConnectionHandler / MessageHandler / ErrorHandler / BasicRateLimitService。
 * - afterConnectionEstablished：从请求头提取用户信息 → 委托 ConnectionHandler 处理连接建立 → 失败则发送错误并关闭。
 * - handleMessage：获取 SessionInfo → 限流检查 → 委托 MessageHandler 处理。
 * - afterConnectionClosed：清理限流计数器 → 委托 ConnectionHandler 处理断开。
 *
 * * [覆盖场景]
 * - Happy Path：有效用户信息连接建立、限流通过时消息处理、正常断开。
 * - Negative Path：用户信息缺失或格式错误拒绝连接、限流触发时拒绝消息、限流清理。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
public class GlobalWebSocketHandlerTest {

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private ConnectionManagerService connectionManager;

    @MockBean
    private SubscriptionManagerService subscriptionManager;

    @MockBean
    private BasicSubscriptionService basicSubscriptionService;

    @MockBean
    private BasicRateLimitService basicRateLimitService;

    @MockBean
    private ChannelMessageRouter channelMessageRouter;

    @Autowired
    private GlobalWebSocketHandler globalWebSocketHandler;

    private WebSocketSession mockSession;
    private String testSessionId;
    private Long testUserId;
    private ConnectionManagerService.SessionInfo sessionInfo;

    @BeforeEach
    void setUp() {
        testSessionId = "global-handler-test-session-" + System.currentTimeMillis();
        testUserId = System.currentTimeMillis();

        mockSession = mock(WebSocketSession.class);
        when(mockSession.getId()).thenReturn(testSessionId);
        when(mockSession.isOpen()).thenReturn(true);

        sessionInfo = new ConnectionManagerService.SessionInfo();
        sessionInfo.setSessionId(testSessionId);
        sessionInfo.setUserId(testUserId);
    }

    // 设置有效的请求头
    private void setValidHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", testUserId.toString());
        headers.add("X-Nickname", "测试用户");
        when(mockSession.getHandshakeHeaders()).thenReturn(headers);
    }

    // 设置空的请求头
    private void setEmptyHeaders() {
        when(mockSession.getHandshakeHeaders()).thenReturn(new HttpHeaders());
    }

    // 设置只有 userId 没有 nickname 的请求头
    private void setOnlyUserIdHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", testUserId.toString());
        when(mockSession.getHandshakeHeaders()).thenReturn(headers);
    }

    // 设置非数字 userId 的请求头
    private void setInvalidUserIdHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", "not-a-number");
        headers.add("X-Nickname", "测试用户");
        when(mockSession.getHandshakeHeaders()).thenReturn(headers);
    }

    // ==================== afterConnectionEstablished ====================

    @Nested
    @DisplayName("afterConnectionEstablished：连接建立")
    class ConnectionEstablishedTests {

        @Test
        @DisplayName("有效用户信息：X-User-Id 和 X-Nickname 均存在，建立成功")
        void testValidUserInfoConnectionEstablished() throws Exception {
            // given
            setValidHeaders();
            lenient().doNothing().when(mockSession).sendMessage(any(TextMessage.class));
            doNothing().when(connectionManager).addGlobalSession(any(), anyLong());

            // when
            globalWebSocketHandler.afterConnectionEstablished(mockSession);

            // then
            verify(connectionManager).addGlobalSession(eq(mockSession), any(Long.class));
        }

        @Test
        @DisplayName("X-User-Id 缺失：发送错误并关闭连接")
        void testMissingUserIdClosesConnection() throws Exception {
            setEmptyHeaders();

            globalWebSocketHandler.afterConnectionEstablished(mockSession);

            verify(mockSession, timeout(500)).close(any(CloseStatus.class));
        }

        @Test
        @DisplayName("X-Nickname 缺失：发送错误并关闭连接")
        void testMissingNicknameClosesConnection() throws Exception {
            setOnlyUserIdHeaders();

            globalWebSocketHandler.afterConnectionEstablished(mockSession);

            verify(mockSession, timeout(500)).close(any(CloseStatus.class));
        }

        @Test
        @DisplayName("X-User-Id 格式错误（非数字）：发送错误并关闭连接")
        void testInvalidUserIdFormatClosesConnection() throws Exception {
            setInvalidUserIdHeaders();

            globalWebSocketHandler.afterConnectionEstablished(mockSession);

            verify(mockSession, timeout(500)).close(any(CloseStatus.class));
        }
    }

    // ==================== handleMessage ====================

    @Nested
    @DisplayName("handleMessage：消息处理与限流")
    class HandleMessageTests {

        @BeforeEach
        void setUpHandleMessage() {
            when(connectionManager.getSessionInfo(testSessionId)).thenReturn(sessionInfo);
        }

        @Test
        @DisplayName("限流拒绝时：发送错误消息，不继续处理")
        void testRateLimitRejectedSendsError() throws Exception {
            when(basicRateLimitService.checkMessageRateLimit(anyLong(), any())).thenReturn(false);

            String json = "{\"type\":\"channel_message\",\"eventType\":\"message\",\"channel\":\"room:chat:123\"}";

            globalWebSocketHandler.handleMessage(mockSession, new TextMessage(json));

            verify(mockSession, times(1)).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("SessionInfo 为 null：发送 SESSION_NOT_FOUND 错误")
        void testNullSessionInfoSendsError() throws Exception {
            when(connectionManager.getSessionInfo(testSessionId)).thenReturn(null);

            String json = "{\"type\":\"heartbeat\"}";

            globalWebSocketHandler.handleMessage(mockSession, new TextMessage(json));

            verify(mockSession, times(1)).sendMessage(any(TextMessage.class));
        }
    }

    // ==================== afterConnectionClosed ====================

    @Nested
    @DisplayName("afterConnectionClosed：连接关闭清理")
    class ConnectionClosedTests {

        @Test
        @DisplayName("正常断开：清理限流计数器")
        void testDisconnectCleansUpRateLimit() throws Exception {
            when(connectionManager.getSessionInfo(testSessionId)).thenReturn(sessionInfo);

            globalWebSocketHandler.afterConnectionClosed(mockSession, CloseStatus.NORMAL);

            verify(basicRateLimitService, times(1)).resetUserMessageRate(testUserId);
        }

        @Test
        @DisplayName("SessionInfo 为 null：userId 为 null，不抛异常")
        void testDisconnectWithNullSessionInfoDoesNotThrow() throws Exception {
            when(connectionManager.getSessionInfo(testSessionId)).thenReturn(null);

            assertDoesNotThrow(() ->
                    globalWebSocketHandler.afterConnectionClosed(mockSession, CloseStatus.NORMAL));

            verify(basicRateLimitService, never()).resetUserMessageRate(anyLong());
        }

        @Test
        @DisplayName("resetUserMessageRate 抛异常时不传播")
        void testResetRateLimitExceptionDoesNotPropagate() throws Exception {
            when(connectionManager.getSessionInfo(testSessionId)).thenReturn(sessionInfo);
            doThrow(new RuntimeException("Redis error"))
                    .when(basicRateLimitService).resetUserMessageRate(anyLong());

            assertDoesNotThrow(() ->
                    globalWebSocketHandler.afterConnectionClosed(mockSession, CloseStatus.NORMAL));
        }
    }

    // ==================== supportsPartialMessages ====================

    @Nested
    @DisplayName("supportsPartialMessages")
    class SupportsPartialMessagesTests {

        @Test
        @DisplayName("不支持部分消息，返回 false")
        void testSupportsPartialMessagesReturnsFalse() {
            assertFalse(globalWebSocketHandler.supportsPartialMessages());
        }
    }
}
