package com.gopair.websocketservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gopair.websocketservice.config.TestConfig;
import com.gopair.websocketservice.protocol.MessageType;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import com.gopair.websocketservice.service.ChannelMessageRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BusinessMessageListener 单元测试。
 *
 * * [核心策略]
 * - ChannelMessageRouter 使用 @MockBean，完全控制消息路由调用。
 * - TracingAmqpConsumerSupport 使用 @MockBean，runWithTracing 直接执行传入的 Runnable。
 * - 使用 Jackson2JsonMessageConverter 序列化真实的 DTO 对象，模拟 RabbitMQ 投递场景。
 *
 * * [覆盖场景]
 * - Happy Path：chat / signaling / file / system 四类消息均被正确路由。
 * - Edge Cases：消息类型未知时降级为 CHAT、payload 中含时间数组归一化、消息缺少字段。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
public class BusinessMessageListenerTest {

    @MockBean
    private ChannelMessageRouter channelMessageRouter;

    @Autowired
    private BusinessMessageListener businessMessageListener;

    private ObjectMapper objectMapper;
    private Jackson2JsonMessageConverter messageConverter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        messageConverter = new Jackson2JsonMessageConverter(objectMapper);
    }

    private Message buildRawMessage(Object dto) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(dto);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        MessageProperties props = new MessageProperties();
        props.setContentType("application/json");
        return new Message(body, props);
    }

    // ==================== Happy Path：四类消息路由 ====================

    @Nested
    @DisplayName("Happy Path：MQ 消息路由")
    class MessageRoutingTests {

        @Test
        @DisplayName("chat 消息被路由到 ChannelMessageRouter")
        void testChatMessageRouted() {
            BusinessMessageListener.WebSocketMessageDto dto = BusinessMessageListener.WebSocketMessageDto.builder()
                    .messageId("msg-chat-001")
                    .type("chat")
                    .channel("room:chat:123")
                    .eventType("message")
                    .payload(Map.of("content", "Hello", "senderId", 1001))
                    .timestamp(LocalDateTime.now())
                    .source("chat-service")
                    .build();

            businessMessageListener.handleChatMessage(dto, buildRawMessage(dto));

            verify(channelMessageRouter, times(1)).processChannelMessage(any(UnifiedWebSocketMessage.class));
        }

        @Test
        @DisplayName("signaling 消息被路由到 ChannelMessageRouter")
        void testSignalingMessageRouted() {
            BusinessMessageListener.WebSocketMessageDto dto = BusinessMessageListener.WebSocketMessageDto.builder()
                    .messageId("msg-sig-001")
                    .type("signaling")
                    .channel("room:chat:456")
                    .eventType("offer")
                    .payload(Map.of("sdp", "v=0...", "type", "offer"))
                    .timestamp(LocalDateTime.now())
                    .source("voice-service")
                    .build();

            businessMessageListener.handleSignalingMessage(dto, buildRawMessage(dto));

            verify(channelMessageRouter, times(1)).processChannelMessage(any(UnifiedWebSocketMessage.class));
        }

        @Test
        @DisplayName("file 消息被路由到 ChannelMessageRouter")
        void testFileMessageRouted() {
            BusinessMessageListener.WebSocketMessageDto dto = BusinessMessageListener.WebSocketMessageDto.builder()
                    .messageId("msg-file-001")
                    .type("file")
                    .channel("room:file:789")
                    .eventType("upload")
                    .payload(Map.of("fileId", "file-123", "fileName", "doc.pdf"))
                    .timestamp(LocalDateTime.now())
                    .source("file-service")
                    .build();

            businessMessageListener.handleFileMessage(dto, buildRawMessage(dto));

            verify(channelMessageRouter, times(1)).processChannelMessage(any(UnifiedWebSocketMessage.class));
        }

        @Test
        @DisplayName("system 消息被路由到 ChannelMessageRouter")
        void testSystemMessageRouted() {
            BusinessMessageListener.WebSocketMessageDto dto = BusinessMessageListener.WebSocketMessageDto.builder()
                    .messageId("msg-sys-001")
                    .type("system")
                    .channel("system:global")
                    .eventType("broadcast")
                    .payload(Map.of("title", "系统公告", "content", "全体通知"))
                    .timestamp(LocalDateTime.now())
                    .source("admin-service")
                    .build();

            businessMessageListener.handleSystemMessage(dto, buildRawMessage(dto));

            verify(channelMessageRouter, times(1)).processChannelMessage(any(UnifiedWebSocketMessage.class));
        }
    }

    // ==================== Happy Path：消息内容转换 ====================

    @Nested
    @DisplayName("Happy Path：消息字段转换")
    class MessageConversionTests {

        @Test
        @DisplayName("chat 消息的 channel、eventType、source 均被正确传递")
        void testChatMessageFieldsPreserved() {
            BusinessMessageListener.WebSocketMessageDto dto = BusinessMessageListener.WebSocketMessageDto.builder()
                    .messageId("msg-fields-001")
                    .type("chat")
                    .channel("room:chat:999")
                    .eventType("message")
                    .payload(Map.of("text", "Test"))
                    .timestamp(LocalDateTime.now())
                    .source("chat-service")
                    .build();

            businessMessageListener.handleChatMessage(dto, buildRawMessage(dto));

            verify(channelMessageRouter).processChannelMessage(argThat(msg ->
                    "room:chat:999".equals(msg.getChannel()) &&
                    "message".equals(msg.getEventType()) &&
                    "chat-service".equals(msg.getSource()) &&
                    MessageType.CHAT.equals(msg.getType())
            ));
        }

        @Test
        @DisplayName("timestamp 为 null 时降级为 LocalDateTime.now()")
        void testNullTimestampDefaultsToNow() {
            BusinessMessageListener.WebSocketMessageDto dto = BusinessMessageListener.WebSocketMessageDto.builder()
                    .messageId("msg-no-ts")
                    .type("chat")
                    .channel("room:chat:111")
                    .eventType("message")
                    .payload(Map.of())
                    .timestamp(null)
                    .build();

            businessMessageListener.handleChatMessage(dto, buildRawMessage(dto));

            verify(channelMessageRouter).processChannelMessage(argThat(msg ->
                    msg.getTimestamp() != null
            ));
        }
    }

    // ==================== Edge Cases：类型降级与时间归一化 ====================

    @Nested
    @DisplayName("Edge Cases：类型降级与时间归一化")
    class EdgeCaseTests {

        @Test
        @DisplayName("未知消息类型降级为 CHAT")
        void testUnknownMessageTypeDefaultsToChat() {
            BusinessMessageListener.WebSocketMessageDto dto = BusinessMessageListener.WebSocketMessageDto.builder()
                    .messageId("msg-unknown")
                    .type("unknown_type")
                    .channel("room:chat:222")
                    .eventType("message")
                    .payload(Map.of())
                    .timestamp(LocalDateTime.now())
                    .build();

            businessMessageListener.handleChatMessage(dto, buildRawMessage(dto));

            verify(channelMessageRouter).processChannelMessage(argThat(msg ->
                    MessageType.CHAT.equals(msg.getType())
            ));
        }

        @Test
        @DisplayName("payload 中时间数组 [yyyy,MM,dd,HH,mm,ss] 归一化时数组长度不足不抛异常")
        void testTimeArrayNormalizationDoesNotThrow() {
            // payload 中时间数组归一化：长度为3时不抛异常（year/month/day 缺 hour/minute/second）
            BusinessMessageListener.WebSocketMessageDto dto = BusinessMessageListener.WebSocketMessageDto.builder()
                    .messageId("msg-time-array")
                    .type("chat")
                    .channel("room:chat:333")
                    .eventType("message")
                    .payload(Map.of(
                            "content", "Test",
                            "createTime", List.of(2026, 4, 15)
                    ))
                    .timestamp(LocalDateTime.now())
                    .build();

            // 验证：消息处理不抛异常，正常路由
            assertDoesNotThrow(() -> businessMessageListener.handleChatMessage(dto, buildRawMessage(dto)));
            verify(channelMessageRouter, times(1)).processChannelMessage(any(UnifiedWebSocketMessage.class));
        }

        @Test
        @DisplayName("payload 为 null 时不抛异常，正常处理")
        void testNullPayloadHandled() {
            BusinessMessageListener.WebSocketMessageDto dto = BusinessMessageListener.WebSocketMessageDto.builder()
                    .messageId("msg-no-payload")
                    .type("chat")
                    .channel("room:chat:444")
                    .eventType("message")
                    .payload(null)
                    .timestamp(LocalDateTime.now())
                    .build();

            assertDoesNotThrow(() -> businessMessageListener.handleChatMessage(dto, buildRawMessage(dto)));
        }

        @Test
        @DisplayName("messageId 为 null 时不抛异常")
        void testNullMessageIdHandled() {
            BusinessMessageListener.WebSocketMessageDto dto = BusinessMessageListener.WebSocketMessageDto.builder()
                    .messageId(null)
                    .type("chat")
                    .channel("room:chat:555")
                    .eventType("message")
                    .payload(Map.of())
                    .timestamp(LocalDateTime.now())
                    .build();

            assertDoesNotThrow(() -> businessMessageListener.handleChatMessage(dto, buildRawMessage(dto)));
        }
    }

    // ==================== Negative Path：异常处理 ====================

    @Nested
    @DisplayName("Negative Path：异常场景")
    class NegativePathTests {

        @Test
        @DisplayName("processChannelMessage 正常路由时不抛异常")
        void testNormalRoutingDoesNotThrow() {
            BusinessMessageListener.WebSocketMessageDto dto = BusinessMessageListener.WebSocketMessageDto.builder()
                    .messageId("msg-normal")
                    .type("chat")
                    .channel("room:chat:666")
                    .eventType("message")
                    .payload(Map.of())
                    .timestamp(LocalDateTime.now())
                    .build();

            assertDoesNotThrow(() -> businessMessageListener.handleChatMessage(dto, buildRawMessage(dto)));
        }
    }
}
