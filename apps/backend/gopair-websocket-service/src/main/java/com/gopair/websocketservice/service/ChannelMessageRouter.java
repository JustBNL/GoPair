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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 频道消息路由服务。
 *
 * <p>职责：
 * <ul>
 *   <li>基于订阅关系，将频道消息路由到对应的 WebSocket 会话</li>
 *   <li>负责单条与批量消息的分发</li>
 * </ul>
 *
 * <p>性能优化：
 * <ul>
 *   <li>序列化复用：每条消息仅序列化一次，所有接收者共享同一 {@link TextMessage} 实例（不可变，线程安全）。</li>
 *   <li>并行分发：使用有界线程池并发向多个 session 发送，降低尾延迟。</li>
 *   <li>串行写保护：同一 session 仍通过 {@code sessionLocks} 保证串行写，防止并发写导致的 IllegalStateException。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelMessageRouter {

    private final SubscriptionManagerService subscriptionManager;
    private final ConnectionManagerService connectionManagerService;
    private final ObjectMapper objectMapper;

    /**
     * 每个 WebSocket session 独立的发送锁，防止多个线程并发写同一个 session
     * 导致 TEXT_PARTIAL_WRITING IllegalStateException。
     */
    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();

    /**
     * 有界线程池，用于并行向多个 session 分发消息。
     *
     * <ul>
     *   <li>核心线程数 4：覆盖常规并发负载。</li>
     *   <li>最大线程数 16：应对短时高并发房间。</li>
     *   <li>队列容量 2000：有界队列防止内存溢出，超出时直接在调用线程执行（CallerRunsPolicy）。</li>
     *   <li>空闲线程存活 60s。</li>
     * </ul>
     */
    private final ThreadPoolExecutor dispatchExecutor = new ThreadPoolExecutor(
            4, 16,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(2000),
            r -> {
                Thread t = new Thread(r, "ws-dispatch-" + r.hashCode());
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

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

            // 性能优化：仅序列化一次，所有接收者复用同一 TextMessage 实例
            // TextMessage 是不可变值对象，多线程共享安全
            final TextMessage textMsg;
            try {
                textMsg = new TextMessage(objectMapper.writeValueAsString(message));
            } catch (Exception e) {
                log.error("[消息代理] 消息序列化失败: messageId={}", message.getMessageId(), e);
                return;
            }

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // 并行分发：为每个 session 创建异步任务
            List<CompletableFuture<Void>> futures = new ArrayList<>(subscriberSessions.size());

            for (String sessionId : subscriberSessions) {
                WebSocketSession session = connectionManagerService.getSession(sessionId);
                if (session != null && session.isOpen()) {
                    String capturedSessionId = sessionId;
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        boolean sent = sendTextMessageToSession(session, textMsg, message.getMessageId());
                        if (sent) {
                            successCount.incrementAndGet();
                            ConnectionManagerService.SessionInfo sessionInfo =
                                    connectionManagerService.getSessionInfo(capturedSessionId);
                            if (sessionInfo != null) {
                                subscriptionManager.updateSubscriptionActivity(
                                        sessionInfo.getUserId(), channel);
                            }
                        } else {
                            failCount.incrementAndGet();
                        }
                    }, dispatchExecutor).exceptionally(ex -> {
                        failCount.incrementAndGet();
                        log.error("[消息代理] 并行分发任务异常: sessionId={}, messageId={}",
                                capturedSessionId, message.getMessageId(), ex);
                        return null;
                    });
                    futures.add(future);
                } else {
                    failCount.incrementAndGet();
                }
            }

            // 等待所有分发任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            log.debug("[消息代理] 频道消息分发完成: channel={}, success={}, fail={}",
                    channel, successCount.get(), failCount.get());

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

    /**
     * 向指定 session 发送已序列化好的 {@link TextMessage}。
     *
     * <p>同一 session 可能被多个分发线程并发调用，
     * {@link org.springframework.web.socket.WebSocketSession#sendMessage} 非线程安全，
     * 必须通过 {@code sessionLocks} 串行化。
     *
     * @param session   目标 WebSocket 会话
     * @param textMsg   已序列化的不可变文本消息（所有接收者复用）
     * @param messageId 消息 ID（仅用于日志）
     * @return true 表示发送成功；false 表示 session 已关闭或发送异常
     */
    private boolean sendTextMessageToSession(WebSocketSession session, TextMessage textMsg, String messageId) {
        Object lock = sessionLocks.computeIfAbsent(session.getId(), id -> new Object());
        synchronized (lock) {
            try {
                if (!session.isOpen()) {
                    log.warn("[消息代理] Session已关闭，跳过发送: sessionId={}, messageId={}",
                            session.getId(), messageId);
                    sessionLocks.remove(session.getId());
                    return false;
                }
                session.sendMessage(textMsg);
                log.debug("[消息代理] WebSocket消息发送成功: sessionId={}, messageId={}",
                        session.getId(), messageId);
                return true;
            } catch (Exception e) {
                log.error("[消息代理] 发送WebSocket消息失败: sessionId={}, messageId={}",
                        session.getId(), messageId, e);
                return false;
            }
        }
    }

    /**
     * 向指定 session 发送消息对象（内部先序列化，供单独调用场景使用）。
     * 保留此方法以兼容可能存在的单独调用场景。
     */
    private void sendMessageToSession(WebSocketSession session, UnifiedWebSocketMessage message) {
        try {
            TextMessage textMsg = new TextMessage(objectMapper.writeValueAsString(message));
            sendTextMessageToSession(session, textMsg, message.getMessageId());
        } catch (Exception e) {
            log.error("[消息代理] 发送WebSocket消息失败（序列化）: sessionId={}, messageId={}",
                    session.getId(), message.getMessageId(), e);
        }
    }
}
