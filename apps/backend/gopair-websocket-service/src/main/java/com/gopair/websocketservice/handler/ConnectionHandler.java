package com.gopair.websocketservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.common.constants.MessageConstants;
import com.gopair.websocketservice.config.RabbitMQConfig;
import com.gopair.websocketservice.protocol.MessageType;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import com.gopair.websocketservice.service.ConnectionManagerService;
import com.gopair.websocketservice.service.BasicSubscriptionService;
import com.gopair.websocketservice.service.RedisOperationService;
import com.gopair.websocketservice.service.SubscriptionManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * WebSocket连接处理器
 * 
 * 职责：
 * - 处理WebSocket连接的建立和断开
 * - 管理连接生命周期
 * - 执行登录时的基础订阅
 * 
 * 架构设计：
 * - 作为GlobalWebSocketHandler的专门处理器
 * - 负责连接相关的所有逻辑
 * - 与ConnectionManagerService和BasicSubscriptionService协作
 * 
 * 使用场景：
 * - 客户端建立WebSocket连接时
 * - 客户端断开WebSocket连接时
 * - 需要清理连接资源时
 * 
 * @author gopair
 */
@Slf4j
@Component
public class ConnectionHandler {

    private final ConnectionManagerService connectionManager;
    private final BasicSubscriptionService basicSubscriptionService;
    private final RedisOperationService redisOperationService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final SubscriptionManagerService subscriptionManager;

    /**
     * 构造函数
     *
     * @param connectionManager 连接管理服务
     * @param basicSubscriptionService 基础订阅服务（使用@Lazy避免循环依赖）
     * @param redisOperationService Redis 操作服务（用于查询用户会话状态）
     * @param rabbitTemplate RabbitMQ 模板（用于发送离线通知）
     * @param objectMapper JSON序列化工具
     */
    public ConnectionHandler(ConnectionManagerService connectionManager,
                           @Lazy BasicSubscriptionService basicSubscriptionService,
                           RedisOperationService redisOperationService,
                           RabbitTemplate rabbitTemplate,
                           ObjectMapper objectMapper,
                           SubscriptionManagerService subscriptionManager) {
        this.connectionManager = connectionManager;
        this.basicSubscriptionService = basicSubscriptionService;
        this.redisOperationService = redisOperationService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.subscriptionManager = subscriptionManager;
    }

    /**
     * 处理连接建立。
     *
     * * [核心策略]
     * - 订阅优先恢复：先将 Redis 中的订阅合并到内存（包含三个反向索引），再执行基础订阅。
     * - 幂等合并：恢复时使用 addAll 而非 put 覆盖，防止基础订阅丢失。
     * - 连接为王：即使欢迎消息发送失败，连接建立仍视为成功。
     *
     * * [执行链路]
     * 1. 验证用户信息，无效则直接返回 false。
     * 2. 恢复 Redis 持久化的订阅状态到内存（channelSessions / sessionSubscriptions / sessionUserMap）。
     * 3. 添加全局连接到 ConnectionManagerService（写入 sessions Map 和 Redis）。
     * 4. 执行登录基础订阅（user:userId + system:global）。
     * 5. 发送连接成功确认消息。
     *
     * @param session WebSocket会话
     * @param userInfo 用户信息（从请求头提取）
     * @return 是否建立成功
     */
    public boolean handleConnectionEstablished(WebSocketSession session, Map<String, Object> userInfo) {
        try {
            log.info("[连接管理] 开始建立WebSocket连接: sessionId={}", session.getId());

            if (!(Boolean) userInfo.get("valid")) {
                String errorReason = (String) userInfo.get("errorReason");
                log.warn("[连接管理] 用户信息验证失败: sessionId={}, reason={}", session.getId(), errorReason);
                return false;
            }

            Long userId = Long.valueOf(userInfo.get("userId").toString());
            String nickname = (String) userInfo.get("nickname");

            // 恢复用户在 Redis 中持久化的订阅状态（合并到内存，并重建三个反向索引）
            // 无需依赖 sessions Map，仅需 userId 和 sessionId 即可重建所有内存索引
            subscriptionManager.restoreUserSubscriptionState(userId, session.getId());

            // 建立全局连接
            connectionManager.addGlobalSession(session, userId);

            // 执行登录基础订阅
            basicSubscriptionService.performLoginBasicSubscription(session.getId(), userId);

            // 发送连接成功确认
            UnifiedWebSocketMessage welcomeMessage = new UnifiedWebSocketMessage()
                    .setMessageId(UUID.randomUUID().toString())
                    .setTimestamp(LocalDateTime.now())
                    .setType(MessageType.CONNECTION)
                    .setEventType("connected")
                    .setPayload(Map.of(
                            "userId", userId,
                            "nickname", nickname,
                            "connectionTime", LocalDateTime.now()
                    ));

            sendWelcomeMessage(session, welcomeMessage);
            
            log.info("[连接管理] WebSocket连接建立成功: sessionId={}, userId={}, nickname={}", 
                    session.getId(), userId, nickname);

            return true;

        } catch (Exception e) {
            log.error("[连接管理] 建立WebSocket连接失败: sessionId={}", session.getId(), e);
            return false;
        }
    }

    /**
     * 处理连接断开。
     *
     * * [核心策略]
     * - 原子读删：先 getSession 读 userId 再删 Redis key，避免 TTL 过期导致读不到 userId。
     * - 内存同步清理：拿到 userId 后立即清理本地订阅索引，避免 sessionSubscriptions / channelSessions / sessionUserMap 残留。
     * - 多端感知：只有 ws:user-sessions:{userId} 全部清空才发送离线通知。
     *
     * * [执行链路]
     * 1. 读 userId 并删 Redis key：removeSessionAndGetUserId 一次性完成原子操作。
     * 2. 清理本地订阅内存：cleanupSessionSubscriptions(sessionId, userId)，在 Redis 删除之后、离线通知之前执行。
     * 3. 检查多端：若用户无其他活跃 session，发送 system.offline 到 room-service。
     *
     * @param session WebSocket会话
     */
    public void handleConnectionClosed(WebSocketSession session) {
        try {
            log.info("[连接管理] WebSocket连接断开: sessionId={}", session.getId());

            // 先读取 userId 再删除 Redis key（先后读写），减少往返并避免 TTL 过期导致读不到 userId
            Long userId = connectionManager.removeSessionAndGetUserId(session.getId());

            // 立即清理本地订阅索引（sessionSubscriptions / channelSessions / sessionUserMap），
            // 必须在 Redis 删除之后执行，否则 cleanupSessionSubscriptions 可以根据 userId 做精确清理
            if (userId != null) {
                subscriptionManager.cleanupSessionSubscriptions(session.getId(), userId);
            }

            // 检查该用户是否还有其他活跃会话，只有全部断开才发离线通知
            if (userId != null) {
                Set<String> remainingSessions = redisOperationService.getUserSessions(userId);
                if (remainingSessions == null || remainingSessions.isEmpty()) {
                    sendUserOfflineEvent(userId);
                }
            }

            log.info("[连接管理] 连接资源清理完成: sessionId={}", session.getId());

        } catch (Exception e) {
            log.error("[连接管理] 处理连接断开失败: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 发送用户离线事件到 MQ，供 room-service 消费后将 room_member.status 更新为离线。
     * 使用 system.offline 路由键，room-service 监听 user.offline.queue 队列。
     *
     * @param userId 离线用户 ID
     */
    private void sendUserOfflineEvent(Long userId) {
        try {
            Map<String, Object> payload = Map.of("userId", userId);
            UnifiedWebSocketMessage message = new UnifiedWebSocketMessage()
                    .setMessageId(UUID.randomUUID().toString())
                    .setTimestamp(LocalDateTime.now())
                    .setType(MessageType.SYSTEM)
                    .setChannel("user:" + userId)
                    .setEventType("offline")
                    .setPayload(payload)
                    .setSource("websocket-service");

            rabbitTemplate.convertAndSend(RabbitMQConfig.WEBSOCKET_EXCHANGE, MessageConstants.ROUTING_KEY_SYSTEM_OFFLINE, message);
            log.info("[连接管理] 发送用户离线事件: userId={}", userId);
        } catch (Exception e) {
            log.error("[连接管理] 发送用户离线事件失败: userId={}", userId, e);
        }
    }

    /**
     * 发送欢迎消息
     * 
     * 流程：
     * 1. 将消息对象序列化为JSON字符串
     * 2. 通过WebSocket会话发送消息
     * 3. 记录发送日志
     * 
     * @param session WebSocket会话
     * @param message 统一WebSocket消息对象
     */
    private void sendWelcomeMessage(WebSocketSession session, UnifiedWebSocketMessage message) {
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            session.sendMessage(new org.springframework.web.socket.TextMessage(messageJson));
            log.debug("[连接管理] 发送欢迎消息成功: sessionId={}", session.getId());
        } catch (Exception e) {
            log.error("[连接管理] 发送欢迎消息失败: sessionId={}", session.getId(), e);
        }
    }
} 