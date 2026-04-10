package com.gopair.websocketservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.websocketservice.config.TestConfig;
import com.gopair.websocketservice.enums.WebSocketErrorCode;
import com.gopair.websocketservice.protocol.MessageType;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ErrorHandler 单元测试。
 *
 * * [核心策略]
 * - sendErrorMessage：序列化为 JSON 并通过 session.sendMessage 发送。
 * - sendErrorAndClose：先发送错误消息，再异步（100ms 后）关闭连接。
 * - 传输错误 / 权限错误：仅记录日志并尝试发送错误消息，不关闭连接。
 *
 * * [覆盖场景]
 * - Happy Path：正常发送错误消息、异步关闭。
 * - Negative Path：session 已关闭时的兜底不抛异常。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
public class ErrorHandlerTest {

    @Autowired
    private ErrorHandler errorHandler;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketSession mockSession;

    @BeforeEach
    void setUp() {
        mockSession = mock(WebSocketSession.class);
        when(mockSession.getId()).thenReturn("error-handler-test-session");
        when(mockSession.isOpen()).thenReturn(true);
    }

    // ==================== Happy Path ====================

    @Nested
    @DisplayName("Happy Path：发送错误消息")
    class SendErrorMessageTests {

        @Test
        @DisplayName("成功发送错误消息，session.sendMessage 被调用一次")
        void testSendErrorMessageSuccess() throws Exception {
            doNothing().when(mockSession).sendMessage(any(TextMessage.class));

            errorHandler.sendErrorMessage(mockSession, WebSocketErrorCode.CONNECTION_ESTABLISH_FAILED,
                    "测试错误消息");

            verify(mockSession, times(1)).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("错误消息包含正确的 errorCode 和 errorMessage")
        void testErrorMessageContent() throws Exception {
            TextMessage[] captured = new TextMessage[1];
            doAnswer(inv -> {
                captured[0] = (TextMessage) inv.getArgument(0);
                return null;
            }).when(mockSession).sendMessage(any(TextMessage.class));

            errorHandler.sendErrorMessage(mockSession, WebSocketErrorCode.CONNECTION_ESTABLISH_FAILED,
                    "具体错误原因");

            String json = captured[0].getPayload();
            assertNotNull(json);
            // CONNECTION_ESTABLISH_FAILED = 20503
            assertTrue(json.contains("\"errorCode\":20503"), "应包含错误码 20503, 实际: " + json);
            assertTrue(json.contains("具体错误原因"), "应包含具体错误信息, 实际: " + json);
        }

        @Test
        @DisplayName("错误消息使用 error 类型（小写）")
        void testErrorMessageUsesErrorType() throws Exception {
            TextMessage[] captured = new TextMessage[1];
            doAnswer(inv -> {
                captured[0] = (TextMessage) inv.getArgument(0);
                return null;
            }).when(mockSession).sendMessage(any(TextMessage.class));

            errorHandler.sendErrorMessage(mockSession, WebSocketErrorCode.CONNECTION_ESTABLISH_FAILED, "测试");

            String json = captured[0].getPayload();
            assertTrue(json.contains("\"type\":\"error\""), "应使用 error 消息类型（小写），实际: " + json);
        }
    }

    @Nested
    @DisplayName("Happy Path：异步关闭连接")
    class SendErrorAndCloseTests {

        @Test
        @DisplayName("sendErrorAndClose 先发送消息再关闭连接")
        void testSendErrorAndCloseSendsMessageAndCloses() throws Exception {
            doNothing().when(mockSession).sendMessage(any(TextMessage.class));

            errorHandler.sendErrorAndClose(mockSession, WebSocketErrorCode.CONNECTION_ESTABLISH_FAILED,
                    "需要关闭的严重错误");

            verify(mockSession, timeout(500).times(1)).sendMessage(any(TextMessage.class));
            verify(mockSession, timeout(500).times(1)).close(any(CloseStatus.class));
        }

        @Test
        @DisplayName("session 已关闭时不发送消息也不关闭")
        void testSendErrorAndCloseWithClosedSession() throws Exception {
            when(mockSession.isOpen()).thenReturn(false);

            errorHandler.sendErrorAndClose(mockSession, WebSocketErrorCode.CONNECTION_ESTABLISH_FAILED,
                    "已关闭的会话");

            verify(mockSession, never()).sendMessage(any(TextMessage.class));
            verify(mockSession, never()).close(any(CloseStatus.class));
        }
    }

    // ==================== Negative Path ====================

    @Nested
    @DisplayName("Negative Path：异常场景")
    class NegativePathTests {

        @Test
        @DisplayName("session 已关闭时 sendErrorMessage 不抛异常")
        void testSendErrorMessageWithClosedSession() {
            when(mockSession.isOpen()).thenReturn(false);

            assertDoesNotThrow(() ->
                    errorHandler.sendErrorMessage(mockSession, WebSocketErrorCode.CONNECTION_ESTABLISH_FAILED,
                            "已关闭会话的错误"));
        }

        @Test
        @DisplayName("sendMessage 抛异常不向外传播")
        void testSendErrorMessageDoesNotThrowOnSendFailure() throws Exception {
            doThrow(new RuntimeException("Connection broken"))
                    .when(mockSession).sendMessage(any(TextMessage.class));

            assertDoesNotThrow(() ->
                    errorHandler.sendErrorMessage(mockSession, WebSocketErrorCode.CONNECTION_ESTABLISH_FAILED,
                            "发送失败的场景"));
        }

        @Test
        @DisplayName("handleTransportError 在 session 打开时发送错误消息")
        void testHandleTransportErrorSendsErrorWhenSessionOpen() throws Exception {
            doNothing().when(mockSession).sendMessage(any(TextMessage.class));

            errorHandler.handleTransportError(mockSession, new RuntimeException("IO error"));

            verify(mockSession, times(1)).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("handleTransportError 在 session 关闭时不发送消息")
        void testHandleTransportErrorWithClosedSession() throws Exception {
            when(mockSession.isOpen()).thenReturn(false);

            errorHandler.handleTransportError(mockSession, new RuntimeException("IO error"));

            verify(mockSession, never()).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("handleMessageProcessingError 正常处理异常")
        void testHandleMessageProcessingError() throws Exception {
            doNothing().when(mockSession).sendMessage(any(TextMessage.class));

            errorHandler.handleMessageProcessingError(mockSession, "invalid json", new RuntimeException("Parse error"));

            verify(mockSession, times(1)).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("handlePermissionError 正常发送权限错误消息，不关闭连接")
        void testHandlePermissionError() throws Exception {
            doNothing().when(mockSession).sendMessage(any(TextMessage.class));

            errorHandler.handlePermissionError(mockSession, "subscribe", "channel not found");

            verify(mockSession, times(1)).sendMessage(any(TextMessage.class));
            verify(mockSession, never()).close(any(CloseStatus.class));
        }

        @Test
        @DisplayName("sendErrorAndClose 中 sendMessage 异常时同步兜底关闭连接")
        void testSendErrorAndCloseFallbackToSyncClose() throws Exception {
            doThrow(new RuntimeException("Send failed"))
                    .when(mockSession).sendMessage(any(TextMessage.class));
            when(mockSession.isOpen()).thenReturn(true);
            doNothing().when(mockSession).close(any(CloseStatus.class));

            errorHandler.sendErrorAndClose(mockSession, WebSocketErrorCode.CONNECTION_ESTABLISH_FAILED,
                    "Fallback close");

            verify(mockSession, timeout(500).atLeastOnce()).close(any(CloseStatus.class));
        }
    }
}
