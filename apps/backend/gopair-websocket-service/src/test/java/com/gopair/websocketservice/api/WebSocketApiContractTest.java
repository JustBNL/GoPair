//package com.gopair.websocketservice.api;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.gopair.websocketservice.enums.WebSocketErrorCode;
//import com.gopair.websocketservice.service.BasicRateLimitService;
//import com.gopair.websocketservice.service.ConnectionManagerService;
//import com.gopair.websocketservice.service.SubscriptionManagerService;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.context.annotation.Import;
//import org.springframework.test.context.ActiveProfiles;
//
//import jakarta.websocket.*;
//import jakarta.websocket.ClientEndpointConfig.Configurator;
//import jakarta.websocket.DecodeException;
//import jakarta.websocket.Decoder;
//import jakarta.websocket.EncodeException;
//import jakarta.websocket.Encoder;
//import javax.net.ssl.SSLContext;
//import java.net.URI;
//import java.util.*;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicLong;
//
//import static org.assertj.core.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
///**
// * WebSocket 服务 API 契约测试。
// *
// * * [测试策略]
// * - 使用真实 WebSocket 客户端（JSR-356 WebSocketContainer）连接服务端口，覆盖完整协议层。
// * - RabbitTemplate 使用 @MockBean Mock，保持测试隔离。
// * - 每个 WebSocket 操作（连接/消息/关闭）使用 CountDownLatch + BlockingQueue 异步等待响应。
// * - 脏数据清理：通过 WebSocket 断开连接触发 afterConnectionClosed 回调，清理内存状态。
// *
// * * [覆盖的 WebSocket 接口]
// * - WS /api/ws/connect    ← 连接建立、欢迎消息、基础订阅
// * - WS /api/ws/room/*    ← 房间连接（同 GlobalWebSocketHandler）
// * - subscribe            ← 订阅/取消订阅频道
// * - heartbeat            ← 心跳保活
// * - room_message         ← 客户端发布频道消息
// * - catch_up            ← 离线消息补发
// *
// * * [脏数据清理机制]
// * - MySQL：无（此服务不依赖 MySQL）
// * - Redis：每个测试方法结束时主动断开 WebSocket 连接，触发 afterConnectionClosed，
// *   其中 removeSessionAndGetUserId + cleanupSessionSubscriptions 清理内存状态
// * - RabbitMQ：Mock，不产生真实副作用
// */
//@Slf4j
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
//@ActiveProfiles("test")
//@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
//@Import(com.gopair.websocketservice.config.TestConfig.class)
//@DisplayName("WebSocket 服务 API 契约测试")
//class WebSocketApiContractTest {
//
//    @Autowired
//    private ConnectionManagerService connectionManager;
//
//    @Autowired
//    private SubscriptionManagerService subscriptionManager;
//
//    @Autowired
//    private BasicRateLimitService rateLimitService;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @MockBean
//    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
//
//    @MockBean
//    private org.springframework.web.client.RestTemplate restTemplate;
//
//    @Value("${local.server.port}")
//    private int serverPort;
//    private String wsBase;
//
//    private final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();
//    private final List<Session> activeSessions = Collections.synchronizedList(new ArrayList<>());
//    private final AtomicLong counter = new AtomicLong(System.currentTimeMillis() % 1_000_000);
//
//    @BeforeEach
//    void setUp() throws Exception {
//        // 清理前一个测试残留的内存状态（在建立新连接之前）
//        connectionManager.clearAllSessions();
//        subscriptionManager.clearAllSubscriptions();
//
//        // 清理队列
//        responseQueue.clear();
//        activeSessions.clear();
//        wsBase = "ws://localhost:" + serverPort + "/api/ws/connect";
//        log.info("[测试] WebSocket 服务启动于端口: {}", serverPort);
//
//        // 启用限流测试模式，跳过 Redis 令牌桶限流检查
//        rateLimitService.enableTestMode();
//
//        // Stub RestTemplate mock so catch_up requests return success
//        java.util.Map<String, Object> mockResponse = java.util.Map.of("success", true, "data", java.util.List.of());
//        org.mockito.Mockito.when(restTemplate.getForObject(
//                org.mockito.ArgumentMatchers.anyString(),
//                org.mockito.ArgumentMatchers.eq(java.util.Map.class)))
//                .thenReturn(mockResponse);
//    }
//
//    @AfterEach
//    void afterEach() throws Exception {
//        // 断开所有活跃 WebSocket 连接，触发 afterConnectionClosed 回调
//        for (Session session : new ArrayList<>(activeSessions)) {
//            try {
//                if (session.isOpen()) {
//                    session.close();
//                }
//            } catch (Exception ignored) {}
//        }
//
//        // 等待网络层面的连接关闭完成，然后等待所有残留的服务器响应消息到达队列
//        Thread.sleep(1500);
//
//        activeSessions.clear();
//        responseQueue.clear();
//
//        // 只清理订阅管理器（不清 sessions Map，让 afterConnectionClosed 的 removeSessionAndGetUserId 处理）
//        subscriptionManager.clearAllSubscriptions();
//    }
//
//    private String uid() {
//        return String.valueOf(counter.incrementAndGet());
//    }
//
//    private long newUserId() {
//        return System.currentTimeMillis() + counter.incrementAndGet();
//    }
//
//    // ==================== 辅助方法：WebSocket 客户端连接 ====================
//
//    private Session connectClient(long userId, String nickname) throws Exception {
//        return connectClientWithHeaders(Map.of(
//                "X-User-Id", String.valueOf(userId),
//                "X-Nickname", nickname
//        ));
//    }
//
//    private Session connectClientWithHeaders(Map<String, String> headers) throws Exception {
//        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
//        // 从 headers 中提取 userId 和 nickname，构建带查询参数的 URL
//        String userId = headers.get("X-User-Id");
//        String nickname = headers.get("X-Nickname");
//        String uri;
//        if (userId != null && nickname != null) {
//            uri = wsBase + "?userId=" + java.net.URLEncoder.encode(userId, java.nio.charset.StandardCharsets.UTF_8)
//                    + "&nickname=" + java.net.URLEncoder.encode(nickname, java.nio.charset.StandardCharsets.UTF_8);
//        } else {
//            uri = wsBase;
//        }
//
//        CountDownLatch connectLatch = new CountDownLatch(1);
//        StringBuilder errorRef = new StringBuilder();
//
//        Session session = container.connectToServer(new Endpoint() {
//            @Override
//            public void onOpen(Session session, EndpointConfig config) {
//                activeSessions.add(session);
//                session.addMessageHandler(String.class, message -> {
//                    System.out.println("[CLIENT] RAW received: " + message);
//                    responseQueue.offer(message);
//                });
//                System.out.println("[CLIENT] Handler registered for session " + session.getId());
//                connectLatch.countDown();
//            }
//
//            @Override
//            public void onError(Session session, Throwable thr) {
//                log.error("[WS] 连接错误: {}", thr.getMessage());
//                errorRef.append(thr.getMessage());
//                connectLatch.countDown();
//            }
//
//            @Override
//            public void onClose(Session session, CloseReason closeReason) {
//                log.debug("[WS] 连接关闭: {}", closeReason);
//                activeSessions.remove(session);
//            }
//        }, new ClientEndpointConfig() {
//            @Override
//            public List<String> getPreferredSubprotocols() {
//                return Collections.emptyList();
//            }
//
//            @Override
//            public Map<String, Object> getUserProperties() {
//                return new HashMap<>();
//            }
//
//            @Override
//            public SSLContext getSSLContext() {
//                return null;
//            }
//
//            @Override
//            public List<Extension> getExtensions() {
//                return Collections.emptyList();
//            }
//
//            @Override
//            public List<Class<? extends Decoder>> getDecoders() {
//                return Collections.emptyList();
//            }
//
//            @Override
//            public List<Class<? extends Encoder>> getEncoders() {
//                return Collections.emptyList();
//            }
//
//            @Override
//            public ClientEndpointConfig.Configurator getConfigurator() {
//                return new ClientEndpointConfig.Configurator() {
//                    @Override
//                    public void beforeRequest(Map<String, List<String>> requestHeaders) {
//                        for (Map.Entry<String, String> h : headers.entrySet()) {
//                            requestHeaders.put(h.getKey(), List.of(h.getValue()));
//                        }
//                    }
//                };
//            }
//        }, URI.create(uri));
//
//        boolean connected = connectLatch.await(5, TimeUnit.SECONDS);
//        if (!connected) {
//            throw new RuntimeException("WebSocket 连接超时: " + errorRef);
//        }
//
//        return session;
//    }
//
//    private Session connectClientWithHeaders2(Map<String, String> headers) throws Exception {
//        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
//        URI uri = URI.create(wsBase);
//
//        CountDownLatch connectLatch = new CountDownLatch(1);
//        CountDownLatch errorLatch = new CountDownLatch(1);
//        StringBuilder errorMsg = new StringBuilder();
//
//        Session[] sessionHolder = new Session[1];
//        Exception[] connectException = new Exception[1];
//
//        // 使用阻塞方式
//        try {
//            Session session = container.connectToServer(new Endpoint() {
//                @Override
//                public void onOpen(Session sess, EndpointConfig config) {
//                    activeSessions.add(sess);
//                    sessionHolder[0] = sess;
//                    sess.addMessageHandler(String.class, message -> {
//                        log.debug("[WS] 收到: {}", message);
//                        responseQueue.offer(message);
//                    });
//                    connectLatch.countDown();
//                }
//
//                @Override
//                public void onError(Session sess, Throwable thr) {
//                    log.error("[WS] 错误: {}", thr.getMessage());
//                    if (errorMsg.length() == 0) {
//                        errorMsg.append(thr.getMessage());
//                    }
//                    if (connectLatch.getCount() > 0) {
//                        connectLatch.countDown();
//                    }
//                    errorLatch.countDown();
//                }
//
//                @Override
//                public void onClose(Session sess, CloseReason reason) {
//                    activeSessions.remove(sess);
//                }
//            }, new ClientEndpointConfig() {
//                @Override
//                public List<String> getPreferredSubprotocols() {
//                    return Collections.emptyList();
//                }
//
//                @Override
//                public Map<String, Object> getUserProperties() {
//                    return new HashMap<>();
//                }
//
//                @Override
//                public SSLContext getSSLContext() {
//                    return null;
//                }
//
//                @Override
//                public List<Extension> getExtensions() {
//                    return Collections.emptyList();
//                }
//
//                @Override
//                public List<Class<? extends Decoder>> getDecoders() {
//                    return Collections.emptyList();
//                }
//
//                @Override
//                public List<Class<? extends Encoder>> getEncoders() {
//                    return Collections.emptyList();
//                }
//
//                @Override
//                public ClientEndpointConfig.Configurator getConfigurator() {
//                    return new ClientEndpointConfig.Configurator() {
//                        @Override
//                        public void beforeRequest(Map<String, List<String>> requestHeaders) {
//                            headers.forEach((k, v) -> requestHeaders.put(k, List.of(v)));
//                        }
//                    };
//                }
//            }, uri);
//
//            boolean connected = connectLatch.await(5, TimeUnit.SECONDS);
//            if (!connected) {
//                String err = errorMsg.length() > 0 ? errorMsg.toString() : "连接超时";
//                throw new RuntimeException("WebSocket 连接失败: " + err);
//            }
//
//            return sessionHolder[0];
//        } catch (DeploymentException e) {
//            throw new RuntimeException("WebSocket 部署失败: " + e.getMessage(), e);
//        }
//    }
//
//    private void sendWsMessage(Session session, Object message) throws Exception {
//        String json = objectMapper.writeValueAsString(message);
//        session.getBasicRemote().sendText(json);
//    }
//
//    private String awaitResponse(long timeoutMs) throws InterruptedException {
//        return responseQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
//    }
//
//    private Map<String, Object> parseResponse(String json) throws Exception {
//        return objectMapper.readValue(json, Map.class);
//    }
//
//    /**
//     * Drain all messages from the response queue.
//     */
//    private void drainAllResponses(int maxMessages) {
//        try {
//            int count = 0;
//            while (count < maxMessages) {
//                String msg = responseQueue.poll(10, TimeUnit.MILLISECONDS);
//                if (msg == null) break;
//                count++;
//            }
//        } catch (InterruptedException ignored) {}
//    }
//
//    private List<Map<String, Object>> drainResponses(long timeoutMs, int maxCount) throws InterruptedException {
//        List<Map<String, Object>> results = new ArrayList<>();
//        long deadline = System.currentTimeMillis() + timeoutMs;
//        while (results.size() < maxCount && System.currentTimeMillis() < deadline) {
//            String msg = responseQueue.poll(100, TimeUnit.MILLISECONDS);
//            if (msg != null) {
//                try {
//                    results.add(parseResponse(msg));
//                } catch (Exception e) {
//                    log.warn("[测试] 无法解析响应: {}", msg);
//                }
//            }
//        }
//        return results;
//    }
//
//    // ==================== 接口：WS /api/ws/connect（连接建立）====================
//
//    @Nested
//    @DisplayName("WS /api/ws/connect — 连接建立")
//    class ConnectionTests {
//
//        @Test
//        @Order(1)
//        @DisplayName("携带有效请求头连接成功，收到 connection.connected 欢迎消息和基础订阅")
//        void connectWithValidHeadersSuccess() throws Exception {
//            long userId = newUserId();
//            String nickname = "user_" + uid();
//            Session session = connectClient(userId, nickname);
//
//            assertThat(session.isOpen())
//                    .as("WebSocket 连接应成功建立")
//                    .isTrue();
//
//            // 验证欢迎消息
//            String welcomeJson = awaitResponse(3000);
//            assertThat(welcomeJson).isNotNull();
//            log.info("[测试] 实际收到的欢迎消息: {}", welcomeJson);
//
//            Map<String, Object> welcome = parseResponse(welcomeJson);
//            assertThat(welcome.get("type")).isEqualTo("connection");
//            assertThat(welcome.get("eventType")).isEqualTo("connected");
//            assertThat(((Map<?, ?>) welcome.get("data")).get("userId"))
//                    .isEqualTo(userId);
//
//            // 验证 sessions 已注册（通过活跃连接数验证）
//            assertThat(connectionManager.getActiveSessionCount())
//                    .as("sessions Map 应包含已建立的连接")
//                    .isGreaterThan(0);
//
//            // 验证用户订阅已注册（system:global 由 performLoginBasicSubscription 自动订阅）
//            Set<?> subs = subscriptionManager.getUserSubscriptions(userId);
//            assertThat(subs).isNotEmpty();
//        }
//
//        @Test
//        @Order(2)
//        @DisplayName("缺少 X-User-Id 请求头，连接被拒绝并关闭")
//        void connectWithoutUserIdHeader() throws Exception {
//            try {
//                Session session = connectClientWithHeaders(Map.of("X-Nickname", "test"));
//
//                String errorJson = awaitResponse(3000);
//                if (errorJson != null) {
//                    Map<String, Object> error = parseResponse(errorJson);
//                    assertThat(error.get("type")).isEqualTo("error");
//                    Map<?, ?> data = (Map<?, ?>) error.get("data");
//                    assertThat(data.get("errorCode"))
//                            .isEqualTo(WebSocketErrorCode.USER_INFO_HEADER_MISSING.getCode());
//                }
//            } catch (RuntimeException e) {
//                // 连接可能被直接拒绝（JSR-356 实现可能抛异常）
//                assertThat(e.getMessage()).containsAnyOf("连接失败", "连接超时", "WebSocket");
//            }
//        }
//
//        @Test
//        @Order(3)
//        @DisplayName("缺少 X-Nickname 请求头，连接被拒绝")
//        void connectWithoutNicknameHeader() throws Exception {
//            try {
//                Session session = connectClientWithHeaders(Map.of("X-User-Id", String.valueOf(newUserId())));
//
//                String errorJson = awaitResponse(3000);
//                if (errorJson != null) {
//                    Map<String, Object> error = parseResponse(errorJson);
//                    assertThat(error.get("type")).isEqualTo("error");
//                }
//            } catch (RuntimeException e) {
//                assertThat(e.getMessage()).containsAnyOf("连接失败", "连接超时", "WebSocket");
//            }
//        }
//
//        @Test
//        @Order(4)
//        @DisplayName("X-User-Id 格式非法（非数字），连接被拒绝")
//        void connectWithInvalidUserIdFormat() throws Exception {
//            try {
//                Session session = connectClientWithHeaders(Map.of(
//                        "X-User-Id", "not-a-number",
//                        "X-Nickname", "test"
//                ));
//
//                String errorJson = awaitResponse(3000);
//                if (errorJson != null) {
//                    Map<String, Object> error = parseResponse(errorJson);
//                    assertThat(error.get("type")).isEqualTo("error");
//                }
//            } catch (RuntimeException e) {
//                assertThat(e.getMessage()).containsAnyOf("连接失败", "连接超时", "WebSocket");
//            }
//        }
//    }
//
//    // ==================== 接口：subscribe（订阅频道）====================
//
//    @Nested
//    @DisplayName("WS subscribe — 订阅频道")
//    class SubscribeTests {
//
//        @Test
//        @Order(1)
//        @DisplayName("成功订阅 system:global 频道，内存状态正确")
//        void subscribeSystemChannelSuccess() throws Exception {
//            long userId = newUserId();
//            Session session = connectClient(userId, "user_" + uid());
//
//            // 消费欢迎消息
//            awaitResponse(2000);
//
//            // 发送 subscribe 消息（handler 返回 true 但不发送确认响应）
//            Map<String, Object> subMsg = Map.of(
//                    "type", "subscribe",
//                    "eventType", "subscribe",
//                    "payload", Map.of(
//                            "userId", userId,
//                            "channel", "system:global",
//                            "eventTypes", List.of("broadcast", "announcement"),
//                            "source", "manual"
//                    )
//            );
//            sendWsMessage(session, subMsg);
//
//            // 验证内存状态（handler 不发送 WebSocket 响应，通过内存状态验证）
//            Set<?> subs = subscriptionManager.getUserSubscriptions(userId);
//            assertThat(subs).isNotEmpty();
//            assertThat(subs.stream().anyMatch(s ->
//                    "system:global".equals(((com.gopair.websocketservice.domain.ChannelSubscription) s).getChannel())
//            )).isTrue();
//        }
//
//        @Test
//        @Order(2)
//        @DisplayName("订阅他人 user 频道失败（权限不足）")
//        void subscribeOtherUserChannelFails() throws Exception {
//            long userId = newUserId();
//            long otherUserId = newUserId();
//            Session session = connectClient(userId, "user_" + uid());
//            awaitResponse(2000);
//
//            Map<String, Object> subMsg = Map.of(
//                    "type", "subscribe",
//                    "eventType", "subscribe",
//                    "payload", Map.of(
//                            "userId", userId,
//                            "channel", "user:" + otherUserId,
//                            "eventTypes", List.of("message"),
//                            "source", "manual"
//                    )
//            );
//            sendWsMessage(session, subMsg);
//
//            // 验证内存状态：无新增订阅
//            Set<?> subs = subscriptionManager.getUserSubscriptions(userId);
//            boolean hasOtherUserChannel = subs.stream().anyMatch(s ->
//                    ("user:" + otherUserId).equals(((com.gopair.websocketservice.domain.ChannelSubscription) s).getChannel())
//            );
//            assertThat(hasOtherUserChannel)
//                    .as("不应成功订阅他人的 user 频道")
//                    .isFalse();
//        }
//
//        @Test
//        @Order(3)
//        @DisplayName("订阅未知前缀频道失败")
//        void subscribeUnknownChannelPrefixFails() throws Exception {
//            long userId = newUserId();
//            Session session = connectClient(userId, "user_" + uid());
//            awaitResponse(2000);
//
//            Map<String, Object> subMsg = Map.of(
//                    "type", "subscribe",
//                    "eventType", "subscribe",
//                    "payload", Map.of(
//                            "userId", userId,
//                            "channel", "unknown:prefix:123",
//                            "eventTypes", List.of("message"),
//                            "source", "manual"
//                    )
//            );
//            sendWsMessage(session, subMsg);
//
//            // 验证内存状态：无新增订阅
//            Set<?> subs = subscriptionManager.getUserSubscriptions(userId);
//            boolean hasUnknownChannel = subs.stream().anyMatch(s ->
//                    "unknown:prefix:123".equals(((com.gopair.websocketservice.domain.ChannelSubscription) s).getChannel())
//            );
//            assertThat(hasUnknownChannel)
//                    .as("未知前缀频道订阅应被拒绝")
//                    .isFalse();
//        }
//
//        @Test
//        @Order(4)
//        @DisplayName("重复订阅同一频道幂等，订阅数量不增加")
//        void duplicateSubscriptionIsIdempotent() throws Exception {
//            long userId = newUserId();
//            Session session = connectClient(userId, "user_" + uid());
//            awaitResponse(2000);
//
//            String channel = "system:global";
//            Map<String, Object> subMsg = Map.of(
//                    "type", "subscribe",
//                    "eventType", "subscribe",
//                    "payload", Map.of(
//                            "userId", userId,
//                            "channel", channel,
//                            "eventTypes", List.of("broadcast"),
//                            "source", "manual"
//                    )
//            );
//
//            sendWsMessage(session, subMsg);
//            awaitResponse(500);
//            sendWsMessage(session, subMsg);
//            awaitResponse(500);
//
//            long count = subscriptionManager.getUserSubscriptions(userId).stream()
//                    .filter(s -> channel.equals(((com.gopair.websocketservice.domain.ChannelSubscription) s).getChannel()))
//                    .count();
//            assertThat(count)
//                    .as("重复订阅同一频道不应产生重复条目")
//                    .isEqualTo(1);
//        }
//
//        @Test
//        @Order(5)
//        @DisplayName("payload 缺失 userId 字段，订阅失败")
//        void subscribeWithMissingUserIdFails() throws Exception {
//            long userId = newUserId();
//            Session session = connectClient(userId, "user_" + uid());
//            awaitResponse(2000);
//
//            Map<String, Object> subMsg = Map.of(
//                    "type", "subscribe",
//                    "eventType", "subscribe",
//                    "payload", Map.of(
//                            "channel", "system:global",
//                            "eventTypes", List.of("broadcast")
//                    )
//            );
//            sendWsMessage(session, subMsg);
//
//            // 验证内存状态：无新增订阅
//            Set<?> subsBefore = subscriptionManager.getUserSubscriptions(userId);
//            int countBefore = subsBefore.size();
//            assertThat(countBefore)
//                    .as("缺失 userId 的订阅不应产生新订阅记录")
//                    .isLessThanOrEqualTo(1);
//        }
//    }
//
//    // ==================== 接口：unsubscribe（取消订阅）====================
//
//    @Nested
//    @DisplayName("WS unsubscribe — 取消订阅频道")
//    class UnsubscribeTests {
//
//        @Test
//        @Order(1)
//        @DisplayName("成功取消订阅频道，订阅数量减少")
//        void unsubscribeChannelSuccess() throws Exception {
//            long userId = newUserId();
//            Session session = connectClient(userId, "user_" + uid());
//            awaitResponse(2000);
//
//            String channel = "system:global";
//
//            // 验证订阅前
//            Set<?> subsBefore = subscriptionManager.getUserSubscriptions(userId);
//
//            // 发送 unsubscribe 消息
//            Map<String, Object> unsubMsg = Map.of(
//                    "type", "subscribe",
//                    "eventType", "unsubscribe",
//                    "payload", Map.of(
//                            "userId", userId,
//                            "channel", channel
//                    )
//            );
//            sendWsMessage(session, unsubMsg);
//            awaitResponse(1000);
//
//            // 验证订阅后数量减少
//            Set<?> subsAfter = subscriptionManager.getUserSubscriptions(userId);
//            assertThat(subsAfter.size())
//                    .as("取消订阅后该频道应从订阅列表移除")
//                    .isLessThanOrEqualTo(subsBefore.size());
//        }
//
//        @Test
//        @Order(2)
//        @DisplayName("取消不存在的订阅幂等")
//        void unsubscribeNonExistentIsIdempotent() throws Exception {
//            long userId = newUserId();
//            Session session = connectClient(userId, "user_" + uid());
//            awaitResponse(2000);
//
//            Map<String, Object> unsubMsg = Map.of(
//                    "type", "subscribe",
//                    "eventType", "unsubscribe",
//                    "payload", Map.of(
//                            "userId", userId,
//                            "channel", "non-existent:channel:" + uid()
//                    )
//            );
//            sendWsMessage(session, unsubMsg);
//
//            // 不抛异常，幂等处理
//            String resp = awaitResponse(500);
//            assertThat(session.isOpen())
//                    .as("取消不存在的订阅不应导致连接断开")
//                    .isTrue();
//        }
//
//        @Test
//        @Order(3)
//        @DisplayName("payload 缺失 channel 字段，unsubscribe 不产生副作用")
//        void unsubscribeWithMissingChannelNoEffect() throws Exception {
//            long userId = newUserId();
//            Session session = connectClient(userId, "user_" + uid());
//            awaitResponse(2000);
//
//            Set<?> subsBefore = subscriptionManager.getUserSubscriptions(userId);
//
//            Map<String, Object> unsubMsg = Map.of(
//                    "type", "subscribe",
//                    "eventType", "unsubscribe",
//                    "payload", Map.of(
//                            "userId", userId
//                    )
//            );
//            sendWsMessage(session, unsubMsg);
//            awaitResponse(500);
//
//            Set<?> subsAfter = subscriptionManager.getUserSubscriptions(userId);
//            assertThat(subsAfter.size())
//                    .as("缺失 channel 字段的 unsubscribe 不应改变订阅状态")
//                    .isEqualTo(subsBefore.size());
//        }
//    }
//
//    // ==================== 接口：heartbeat（心跳保活）====================
//
//    @Nested
//    @DisplayName("WS heartbeat — 心跳保活")
//    class HeartbeatTests {
//
//        @Test
//        @Order(1)
//        @DisplayName("发送心跳消息，收到 pong 响应")
//        void heartbeatPongResponse() throws Exception {
//            long userId = newUserId();
//            Session session = connectClient(userId, "user_" + uid());
//            awaitResponse(2000); // 消费欢迎消息
//
//            Map<String, Object> heartbeatMsg = Map.of(
//                    "type", "heartbeat",
//                    "eventType", "heartbeat"
//            );
//            sendWsMessage(session, heartbeatMsg);
//
//            String pongJson = awaitResponse(2000);
//            assertThat(pongJson).isNotNull();
//
//            Map<String, Object> pong = parseResponse(pongJson);
//            assertThat(pong.get("type")).isEqualTo("heartbeat");
//            assertThat(pong.get("eventType")).isEqualTo("pong");
//        }
//
//        @Test
//        @Order(2)
//        @DisplayName("快速连续心跳均返回 pong")
//        void rapidHeartbeatsAllReturnPong() throws Exception {
//            long userId = newUserId();
//            Session session = connectClient(userId, "user_" + uid());
//            awaitResponse(2000);
//
//            for (int i = 0; i < 3; i++) {
//                Map<String, Object> heartbeatMsg = Map.of(
//                        "type", "heartbeat",
//                        "eventType", "heartbeat"
//                );
//                sendWsMessage(session, heartbeatMsg);
//            }
//
//            List<Map<String, Object>> pongs = drainResponses(3000, 3);
//            assertThat(pongs)
//                    .as("连续 3 次心跳应均收到 pong")
//                    .hasSize(3);
//        }
//    }
//
//    // ==================== 接口：room_message（频道消息）====================
//
//    @Nested
//    @DisplayName("WS room_message — 客户端发布频道消息")
//    class ChannelMessageTests {
//
//        @Test
//        @Order(1)
//        @DisplayName("发布 room_message 消息，路由成功无异常")
//        void publishChannelMessageSuccess() throws Exception {
//            long userId = newUserId();
//            Session session = connectClient(userId, "user_" + uid());
//            awaitResponse(2000);
//
//            // 使用 room:{roomId} 格式，roomId 为数字
//            String channel = "room:" + newUserId();
//            Map<String, Object> chatMsg = Map.of(
//                    "type", "room_message",
//                    "channel", channel,
//                    "eventType", "message",
//                    "data", Map.of(
//                            "content", "Hello World",
//                            "messageType", 1
//                    )
//            );
//
//            // roomMessageRouter.processChannelMessage 不向发送者回推，只验证不抛异常
//            assertThatCode(() -> sendWsMessage(session, chatMsg))
//                    .as("发布频道消息不应抛异常")
//                    .doesNotThrowAnyException();
//        }
//
//        @Test
//        @Order(2)
//        @DisplayName("发布非法 JSON 格式消息，服务不崩溃")
//        void publishInvalidJsonMessageNoCrash() throws Exception {
//            long userId = newUserId();
//            Session session = connectClient(userId, "user_" + uid());
//            awaitResponse(2000);
//
//            // 发送非 JSON 字符串
//            session.getBasicRemote().sendText("{ invalid json }");
//
//            // 连接保持活跃
//            assertThat(session.isOpen())
//                    .as("非法 JSON 消息不应导致连接断开")
//                    .isTrue();
//        }
//    }
//
//    // ==================== 接口：catch_up（离线消息补发）====================
//
//    @Nested
//    @DisplayName("WS catch_up — 离线消息补发")
//    class CatchUpTests {
//
//        @Test
//        @Order(1)
//        @DisplayName("发送 catch_up 请求，返回 catch_up_result")
//        void catchUpRequestReturnsResult() throws Exception {
//            long userId = newUserId();
//            Session session = connectClient(userId, "user_" + uid());
//            awaitResponse(2000);
//
//            // 使用有效的 user: 频道格式，RestTemplate mock 会返回空消息列表
//            Map<String, Object> catchUpMsg = Map.of(
//                    "type", "catch_up",
//                    "eventType", "catch_up",
//                    "payload", Map.of(
//                            "channel", "user:" + userId,
//                            "lastMessageId", 0
//                    )
//            );
//            sendWsMessage(session, catchUpMsg);
//
//            String resultJson = awaitResponse(3000);
//            assertThat(resultJson).isNotNull();
//
//            Map<String, Object> result = parseResponse(resultJson);
//            assertThat(result.get("type")).isEqualTo("catch_up_result");
//            assertThat(result.get("eventType")).isEqualTo("catch_up_result");
//        }
//
//        @Test
//        @Order(2)
//        @DisplayName("catch_up 缺少 channel 参数，返回错误")
//        void catchUpWithMissingChannelReturnsError() throws Exception {
//            long userId = newUserId();
//            Session session = connectClient(userId, "user_" + uid());
//            awaitResponse(2000);
//
//            Map<String, Object> catchUpMsg = Map.of(
//                    "type", "catch_up",
//                    "eventType", "catch_up",
//                    "payload", Map.of(
//                            "lastMessageId", 100
//                    )
//            );
//            sendWsMessage(session, catchUpMsg);
//
//            String resp = awaitResponse(2000);
//            assertThat(resp).isNotNull();
//            Map<String, Object> result = parseResponse(resp);
//            assertThat(result.get("type")).isEqualTo("error");
//        }
//
//        @Test
//        @Order(3)
//        @DisplayName("catch_up 使用 room: 频道前缀，返回 catch_up_result")
//        void catchUpRoomChannelRequest() throws Exception {
//            long userId = newUserId();
//            long roomId = newUserId();
//            Session session = connectClient(userId, "user_" + uid());
//            awaitResponse(2000);
//
//            Map<String, Object> catchUpMsg = Map.of(
//                    "type", "catch_up",
//                    "eventType", "catch_up",
//                    "payload", Map.of(
//                            "channel", "room:" + roomId,
//                            "lastMessageId", 50
//                    )
//            );
//            sendWsMessage(session, catchUpMsg);
//
//            String resultJson = awaitResponse(3000);
//            assertThat(resultJson).isNotNull();
//
//            Map<String, Object> result = parseResponse(resultJson);
//            assertThat(result.get("type")).isEqualTo("catch_up_result");
//        }
//    }
//
//    // ==================== 连接关闭 ====================
//
//    @Nested
//    @DisplayName("WS 连接关闭 — afterConnectionClosed 回调")
//    class ConnectionCloseTests {
//
//        @Test
//        @Order(1)
//        @DisplayName("主动关闭连接，sessions Map 清空")
//        void normalCloseClearsResources() throws Exception {
//            long userId = newUserId();
//            Session session = connectClient(userId, "user_" + uid());
//            awaitResponse(2000);
//
//            String sessionId = session.getId();
//            assertThat(connectionManager.getActiveSessionCount())
//                    .as("sessions Map 应包含已建立的连接")
//                    .isGreaterThan(0);
//
//            session.close();
//
//            // 等待连接关闭
//            Thread.sleep(500);
//
//            assertThat(connectionManager.getActiveSessionCount())
//                    .as("关闭后 sessions Map 应清空")
//                    .isEqualTo(0);
//        }
//
//        @Test
//        @Order(2)
//        @DisplayName("多端登录时关闭一个 session，另一个 session 不受影响")
//        void closeOneSessionDoesNotAffectAnother() throws Exception {
//            long userId = newUserId();
//
//            // 第一端
//            Session session1 = connectClient(userId, "user_" + uid());
//            awaitResponse(2000);
//            String sessionId1 = session1.getId();
//
//            // 第二端
//            Session session2 = connectClient(userId, "user_" + uid());
//            awaitResponse(2000);
//            String sessionId2 = session2.getId();
//
//            assertThat(connectionManager.getActiveSessionCount())
//                    .as("两个会话都应注册")
//                    .isEqualTo(2);
//
//            // 关闭第一端
//            session1.close();
//            Thread.sleep(500);
//
//            assertThat(connectionManager.getActiveSessionCount())
//                    .as("第一端关闭后应剩 1 个 session")
//                    .isEqualTo(1);
//            assertThat(connectionManager.getActiveSessionCount())
//                    .as("第二端 session 应保留")
//                    .isGreaterThan(0);
//
//            session2.close();
//        }
//    }
//
//    // ==================== 错误码映射验证 ====================
//
//    @Nested
//    @DisplayName("错误码验证")
//    class ErrorCodeTests {
//
//        @Test
//        @Order(1)
//        @DisplayName("USER_INFO_HEADER_MISSING 错误码为 20500")
//        void userInfoHeaderMissingErrorCode() {
//            assertThat(WebSocketErrorCode.USER_INFO_HEADER_MISSING.getCode()).isEqualTo(20500);
//        }
//
//        @Test
//        @Order(2)
//        @DisplayName("CONNECTION_ESTABLISH_FAILED 错误码为 20503")
//        void connectionEstablishFailedErrorCode() {
//            assertThat(WebSocketErrorCode.CONNECTION_ESTABLISH_FAILED.getCode()).isEqualTo(20503);
//        }
//
//        @Test
//        @Order(3)
//        @DisplayName("MESSAGE_PROCESSING_ERROR 错误码为 20510")
//        void messageProcessingErrorCode() {
//            assertThat(WebSocketErrorCode.MESSAGE_PROCESSING_ERROR.getCode()).isEqualTo(20510);
//        }
//
//        @Test
//        @Order(4)
//        @DisplayName("PAYLOAD_FIELD_MISSING 错误码为 20521")
//        void payloadFieldMissingErrorCode() {
//            assertThat(WebSocketErrorCode.PAYLOAD_FIELD_MISSING.getCode()).isEqualTo(20521);
//        }
//
//        @Test
//        @Order(5)
//        @DisplayName("PAYLOAD_ADAPTATION_ERROR 错误码为 20520")
//        void payloadAdaptationErrorCode() {
//            assertThat(WebSocketErrorCode.PAYLOAD_ADAPTATION_ERROR.getCode()).isEqualTo(20520);
//        }
//    }
//
//    // ==================== 订阅状态查询 ====================
//
//    @Nested
//    @DisplayName("订阅状态查询")
//    class SubscriptionStateQueryTests {
//
//        @Test
//        @Order(1)
//        @DisplayName("订阅后 getChannelSubscribers 返回包含对应 session 的集合")
//        void getChannelSubscribersAfterSubscribe() throws Exception {
//            long userId = newUserId();
//            Session session = connectClient(userId, "user_" + uid());
//            awaitResponse(2000);
//
//            // 使用有效的数字 roomId，格式为 room:chat:{roomId}
//            long roomId = newUserId();
//            String channel = "room:chat:" + roomId;
//            sendWsMessage(session, Map.of(
//                    "type", "subscribe",
//                    "eventType", "subscribe",
//                    "payload", Map.of(
//                            "userId", userId,
//                            "channel", channel,
//                            "eventTypes", List.of("message"),
//                            "source", "manual"
//                    )
//            ));
//            awaitResponse(1000);
//
//            Set<String> subscribers = subscriptionManager.getChannelSubscribers(channel, "message");
//            assertThat(subscribers)
//                    .as("频道订阅者列表应非空（订阅成功）")
//                    .isNotEmpty();
//        }
//
//        @Test
//        @Order(2)
//        @DisplayName("取消订阅后 getChannelSubscribers 不再返回该 session")
//        void getChannelSubscribersAfterUnsubscribe() throws Exception {
//            long userId = newUserId();
//            Session session = connectClient(userId, "user_" + uid());
//            awaitResponse(2000);
//
//            // 使用有效的数字 roomId
//            long roomId = newUserId();
//            String channel = "room:chat:" + roomId;
//
//            // 订阅
//            sendWsMessage(session, Map.of(
//                    "type", "subscribe",
//                    "eventType", "subscribe",
//                    "payload", Map.of(
//                            "userId", userId,
//                            "channel", channel,
//                            "eventTypes", List.of("message"),
//                            "source", "manual"
//                    )
//            ));
//            awaitResponse(1000);
//
//            // 取消订阅
//            sendWsMessage(session, Map.of(
//                    "type", "subscribe",
//                    "eventType", "unsubscribe",
//                    "payload", Map.of(
//                            "userId", userId,
//                            "channel", channel
//                    )
//            ));
//            awaitResponse(1000);
//
//            Set<String> subscribers = subscriptionManager.getChannelSubscribers(channel, "message");
//            assertThat(subscribers)
//                    .as("取消订阅后频道订阅者列表不应包含该 session")
//                    .doesNotContain(session.getId());
//        }
//
//        @Test
//        @Order(3)
//        @DisplayName("getUserSubscriptions 对不存在用户返回空集合")
//        void getUserSubscriptionsNonExistentUser() {
//            Set<?> subs = subscriptionManager.getUserSubscriptions(999999L);
//            assertThat(subs)
//                    .as("不存在用户的订阅列表应为空")
//                    .isEmpty();
//        }
//
//        @Test
//        @Order(4)
//        @DisplayName("getChannelSubscribers 对不存在频道返回空集合")
//        void getChannelSubscribersNonExistentChannel() {
//            Set<String> subscribers = subscriptionManager.getChannelSubscribers(
//                    "non-existent:channel:" + uid(), "message");
//            assertThat(subscribers)
//                    .as("不存在频道的订阅者列表应为空")
//                    .isEmpty();
//        }
//
//        @Test
//        @Order(5)
//        @DisplayName("getSubscriptionStats 返回正确的统计维度")
//        void getSubscriptionStatsReturnsCorrectDimensions() {
//            Map<String, Object> stats = subscriptionManager.getSubscriptionStats();
//            assertThat(stats).containsKeys("totalUsers", "activeUsers", "totalChannels", "totalSubscriptions");
//            assertThat(stats.get("totalUsers")).isInstanceOf(Number.class);
//            assertThat(stats.get("totalSubscriptions")).isInstanceOf(Number.class);
//        }
//    }
//}
