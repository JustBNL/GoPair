package com.gopair.voiceservice.base;

import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.common.util.TracingAmqpConsumerSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 语音通话服务集成测试配置。
 *
 * * [核心策略]
 * - Byte Buddy 1.14.x 不支持 Java 23，所有外部依赖通过手动 stub 类注入，避免 Mockito。
 * - WebSocketMessageProducer：RecordingWebSocketProducer（录音式，支持测试断言，独立于 RabbitMQ）。
 * - RabbitMQ：延迟连接模式，连接失败不影响测试（RecordingWebSocketProducer 不依赖 RabbitMQ）。
 * - TracingAmqpConsumerSupport：手动 stub（runWithTracing 直接执行 task）。
 *
 * @author gopair
 */
@Slf4j
@TestConfiguration
public class VoiceServiceTestConfig {

    // ==================== RabbitMQ（延迟连接模式） ====================

    @Bean
    @Primary
    public CachingConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername("guest");
        factory.setPassword("guest");
        factory.setCacheMode(CachingConnectionFactory.CacheMode.CONNECTION);
        factory.setConnectionCacheSize(1);
        factory.setChannelCacheSize(1);
        factory.setConnectionTimeout(500);
        return factory;
    }

    @Bean
    @Primary
    public RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory) {
        return new RabbitTemplate(connectionFactory);
    }

    // ==================== RestTemplate ====================

    /**
     * Mock RestTemplate（Primary）：用于 Service 层调用外部服务时使用，
     * 拦截器按 URL 匹配返回预设 stub。
     */
    @Bean
    @Primary
    public RestTemplate mockRestTemplate() {
        RestTemplate restTemplate = new RestTemplate(new SimpleClientHttpRequestFactory());
        restTemplate.getInterceptors().add(new VoiceServiceMockRestInterceptor());
        return restTemplate;
    }

    /**
     * Real RestTemplate：测试代码向 localhost 发送 HTTP 请求时使用，
     * 走真实网络连接，确保 Controller 请求能正确到达应用。
     */
    @Bean
    public RestTemplate realRestTemplate() {
        return new RestTemplate();
    }

    // ==================== 辅助 Bean（避免 Byte Buddy + Java 23 不兼容） ====================

    /**
     * Recording WebSocket Producer：继承 WebSocketMessageProducer 但不依赖 RabbitMQ。
     * 所有 convertAndSend 调用 no-op，记录参数供断言使用。
     */
    @Bean
    @Primary
    public WebSocketMessageProducer webSocketMessageProducer() {
        return new RecordingWebSocketProducer();
    }

    @Bean
    @Primary
    public TracingAmqpConsumerSupport tracingAmqpConsumerSupport() {
        return new TracingAmqpConsumerSupportStub();
    }

    // ==================== Stub 实现 ====================

    /**
     * Recording WebSocket Producer：录音式消息推送记录器。
     * - 继承 WebSocketMessageProducer 作为 primary bean 注入 VoiceCallServiceImpl。
     * - 不依赖 RabbitMQ，所有 convertAndSend 调用 no-op。
     * - 记录所有调用参数，支持测试断言。
     */
    private static class RecordingWebSocketProducer extends WebSocketMessageProducer {

        private final List<RoomEvent> roomEvents = new CopyOnWriteArrayList<>();
        private final List<UserMessage> userMessages = new CopyOnWriteArrayList<>();

        public RecordingWebSocketProducer() {
            super(null);
        }

        @Override
        public void sendEventToRoom(Long roomId, String eventType, Map<String, Object> payload) {
            roomEvents.add(new RoomEvent(roomId, eventType, payload));
        }

        @Override
        public void sendSignalingMessage(Long userId, Map<String, Object> payload) {
            userMessages.add(new UserMessage(userId, payload));
        }

        public void reset() {
            roomEvents.clear();
            userMessages.clear();
        }

        public List<RoomEvent> getRoomEvents() {
            return Collections.unmodifiableList(roomEvents);
        }

        public List<UserMessage> getUserMessages() {
            return Collections.unmodifiableList(userMessages);
        }

        public int countRoomEvents(Long roomId, String eventType) {
            return (int) roomEvents.stream()
                    .filter(e -> Objects.equals(e.roomId, roomId) && Objects.equals(e.eventType, eventType))
                    .count();
        }

        public int countSignalingTo(Long userId) {
            return (int) userMessages.stream()
                    .filter(m -> Objects.equals(m.userId, userId))
                    .count();
        }

        private record RoomEvent(Long roomId, String eventType, Map<String, Object> payload) {}
        private record UserMessage(Long userId, Map<String, Object> payload) {}
    }

    /**
     * TracingAmqpConsumerSupport stub：runWithTracing 直接执行 task，不做 MDC 追踪。
     */
    private static class TracingAmqpConsumerSupportStub extends TracingAmqpConsumerSupport {
        @Override
        public void runWithTracing(org.springframework.amqp.core.Message message, Runnable task) {
            task.run();
        }
    }

    // ==================== RestTemplate 拦截器 ====================

    private static class VoiceServiceMockRestInterceptor implements org.springframework.http.client.ClientHttpRequestInterceptor {
        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) throws java.io.IOException {
            return execution.execute(request, body);
        }
    }
}
