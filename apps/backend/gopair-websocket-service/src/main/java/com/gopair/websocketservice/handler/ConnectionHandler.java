package com.gopair.websocketservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.websocketservice.config.RabbitMQConfig;
import com.gopair.websocketservice.protocol.MessageType;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import com.gopair.websocketservice.service.ConnectionManagerService;
import com.gopair.websocketservice.service.BasicSubscriptionService;
import com.gopair.websocketservice.service.ConnectionManagerService.SessionInfo;
import com.gopair.websocketservice.service.RedisOperationService;
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
                           ObjectMapper objectMapper) {
        this.connectionManager = connectionManager;
        this.basicSubscriptionService = basicSubscriptionService;
        this.redisOperationService = redisOperationService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理连接建立
     * 
     * 流程：
     * 1. 验证用户信息
     * 2. 添加全局连接到连接管理器
     * 3. 执行登录基础订阅
     * 4. 发送欢迎消息
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
     * 处理连接断开
     *
     * <h2>完整流程</h2>
     * <ol>
     *   <li><b>清理连接</b>：调用 connectionManager.removeSession，移除 Redis 中的会话索引。</li>
     *   <li><b>检查离线</b>：从 Redis 中读取该用户是否还有其他活跃会话（ws:user-sessions:{userId}）。</li>
     *   <li><b>发离线通知</b>：若用户没有任何其他活跃会话，通过 RabbitMQ 发送 system.offline 消息到 room-service。</li>
     * </ol>
     *
     * <h2>多端登录处理</h2>
     * 用户在手机和电脑上同时在线时，任一端断开只清理自己的 session，
     * 只有当 ws:user-sessions:{userId} 全部清空后才会发送离线通知。
     *
     * @param session WebSocket会话
     */
    public void handleConnectionClosed(WebSocketSession session) {
        try {
            log.info("[连接管理] WebSocket连接断开: sessionId={}", session.getId());

            // 从 Redis 获取会话信息（userId 在 removeSession 之前读取）
            SessionInfo sessionInfo = connectionManager.getSessionInfo(session.getId());
            Long userId = sessionInfo != null ? sessionInfo.getUserId() : null;

            // 清理连接相关资源
            connectionManager.removeSession(session.getId());

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

            rabbitTemplate.convertAndSend(RabbitMQConfig.WEBSOCKET_EXCHANGE, "system.offline", message);
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