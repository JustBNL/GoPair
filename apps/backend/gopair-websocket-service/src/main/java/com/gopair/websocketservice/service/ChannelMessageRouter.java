package com.gopair.websocketservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 频道消息路由服务。
 *
 * 职责：
 * - 基于订阅关系，将频道消息路由到对应的 WebSocket 会话
 * - 负责单条与批量消息的分发
 *
 * 设计：
 * - 仅依赖订阅管理与连接管理服务，不关心底层存储实现
 * - 保持与原 ConnectionManagerService 中路由逻辑等价
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelMessageRouter {

    private final SubscriptionManagerService subscriptionManager;
    private final ConnectionManagerService connectionManagerService;
    private final ObjectMapper objectMapper;

    /**
     * 每个 WebSocket session 独立的发送锁，防止多个 RabbitMQ 消费线程并发写同一个 session
     * 导致 TEXT_PARTIAL_WRITING IllegalStateException。
     * <p>
     * WebSocketSession.sendMessage() 不是线程安全的：Tomcat 的 WsRemoteEndpointImplBase
     * 内部维护状态机，若两个线程同时调用 sendText() 则抛出
     * "The remote endpoint was in state [TEXT_PARTIAL_WRITING]"。
     * 使用 computeIfAbsent 保证每个 sessionId 只有一个锁对象，synchronized 块保证串行发送。
     */
    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();

    public void processChannelMessage(UnifiedWebSocketMessage message) {
        try {
            String channel = message.getChannel();
            if (channel == null || channel.trim().isEmpty()) {
                log.error("[消息代理] 频道名称为空，无法处理消息: messageId={}", message.getMessageId());
                return;
            }

            log.debug("[消息代理] 处理频道消息: channel={}, eventType={}, messageId={}",
                    channel, message.getEventType(), message.getMessageId());

            routeMessageToChannel(channel, message.getEventType(), message);

        } catch (Exception e) {
            log.error("[消息代理] 处理频道消息失败: messageId={}", message.getMessageId(), e);
        }
    }

    public void routeMessageToChannel(String channel, String eventType, UnifiedWebSocketMessage message) {
        try {
            Set<String> subscriberSessions = subscriptionManager.getChannelSubscribers(channel, eventType);

            if (subscriberSessions.isEmpty()) {
                log.warn("[消息代理] 频道无订阅者: channel={}, eventType={}", channel, eventType);
                return;
            }

            int successCount = 0;
            int failCount = 0;

            for (String sessionId : subscriberSessions) {
                WebSocketSession session = connectionManagerService.getSession(sessionId);
                if (session != null && session.isOpen()) {
                    sendMessageToSession(session, message);
                    successCount++;
                    ConnectionManagerService.SessionInfo sessionInfo = connectionManagerService.getSessionInfo(sessionId);
                    if (sessionInfo != null) {
                        subscriptionManager.updateSubscriptionActivity(sessionInfo.getUserId(), channel);
                    }
                } else {
                    failCount++;
                }
            }

            log.debug("[消息代理] 频道消息分发完成: channel={}, success={}, fail={}",
                    channel, successCount, failCount);

        } catch (Exception e) {
            log.error("[消息代理] 频道消息路由失败: channel={}", channel, e);
        }
    }

    public void processBatchMessages(List<UnifiedWebSocketMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        Map<String, List<UnifiedWebSocketMessage>> channelGroups = new HashMap<>();

        for (UnifiedWebSocketMessage message : messages) {
            String channel = message.getChannel();
            if (channel != null) {
                channelGroups.computeIfAbsent(channel, k -> new ArrayList<>()).add(message);
            } else {
                log.error("[消息代理] 消息缺少频道信息，跳过处理: messageId={}", message.getMessageId());
            }
        }

        for (Map.Entry<String, List<UnifiedWebSocketMessage>> entry : channelGroups.entrySet()) {
            for (UnifiedWebSocketMessage message : entry.getValue()) {
                processChannelMessage(message);
            }
        }

        log.debug("[消息代理] 批量消息处理完成: totalCount={}, channels={}",
                messages.size(), channelGroups.size());
    }

    private void sendMessageToSession(WebSocketSession session, UnifiedWebSocketMessage message) {
        // 同一个 session 可能被多个 RabbitMQ 消费线程并发调用，
        // WebSocketSession.sendMessage() 非线程安全，必须串行化。
        Object lock = sessionLocks.computeIfAbsent(session.getId(), id -> new Object());
        synchronized (lock) {
            try {
                if (!session.isOpen()) {
                    log.warn("[消息代理] Session已关闭，跳过发送: sessionId={}, messageId={}",
                            session.getId(), message.getMessageId());
                    sessionLocks.remove(session.getId());
                    return;
                }
                String jsonMessage = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(jsonMessage));
                log.debug("[消息代理] WebSocket消息发送成功: sessionId={}, messageId={}",
                        session.getId(), message.getMessageId());
            } catch (Exception e) {
                log.error("[消息代理] 发送WebSocket消息失败: sessionId={}, messageId={}",
                        session.getId(), message.getMessageId(), e);
            }
        }
    }
}
