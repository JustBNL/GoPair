package com.gopair.websocketservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket连接管理服务
 * 
 * @author gopair
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionManagerService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SubscriptionManagerService subscriptionManager;
    private final ObjectMapper objectMapper;

    /**
     * 本地会话存储
     * Key: sessionId, Value: WebSocketSession
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 用户会话映射
     * Key: userId, Value: sessionId集合
     */
    private final Map<Long, CopyOnWriteArraySet<String>> userSessions = new ConcurrentHashMap<>();

    /**
     * 房间会话映射
     * Key: roomId, Value: sessionId集合
     */
    private final Map<Long, CopyOnWriteArraySet<String>> roomSessions = new ConcurrentHashMap<>();

    /**
     * 会话元数据映射
     * Key: sessionId, Value: SessionInfo
     */
    private final Map<String, SessionInfo> sessionInfos = new ConcurrentHashMap<>();

    /**
     * 添加全局WebSocket会话（新方法）
     * 
     * @param session WebSocket会话
     * @param userId 用户ID
     */
    public void addGlobalSession(WebSocketSession session, Long userId) {
        String sessionId = session.getId();
        
        // 本地存储
        sessions.put(sessionId, session);
        
        // 用户会话映射
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(sessionId);
        
        // 会话信息（全局连接无房间ID）
        SessionInfo sessionInfo = new SessionInfo();
        sessionInfo.setSessionId(sessionId);
        sessionInfo.setUserId(userId);
        sessionInfo.setRoomId(null); // 全局连接无固定房间
        sessionInfo.setConnectTime(LocalDateTime.now());
        sessionInfo.setLastHeartbeat(LocalDateTime.now());
        sessionInfo.setConnectionType("global"); // 标记为全局连接
        
        sessionInfos.put(sessionId, sessionInfo);
        
        // Redis存储（用于跨实例查询）
        String redisKey = "ws:session:" + sessionId;
        redisTemplate.opsForHash().putAll(redisKey, Map.of(
                "userId", userId,
                "roomId", "", // 全局连接无房间ID
                "connectionType", "global",
                "connectTime", LocalDateTime.now().toString(),
                "instanceId", getInstanceId()
        ));
        redisTemplate.expire(redisKey, 3600, TimeUnit.SECONDS);
        
        log.info("[连接管理] 添加全局WebSocket会话: sessionId={}, userId={}", sessionId, userId);
    }

    /**
     * 移除WebSocket会话
     */
    public void removeSession(String sessionId) {
        SessionInfo sessionInfo = sessionInfos.remove(sessionId);
        sessions.remove(sessionId);
        
        if (sessionInfo != null) {
            // 清理用户会话映射
            Long userId = sessionInfo.getUserId();
            if (userId != null) {
                CopyOnWriteArraySet<String> userSessionSet = userSessions.get(userId);
                if (userSessionSet != null) {
                    userSessionSet.remove(sessionId);
                    if (userSessionSet.isEmpty()) {
                        userSessions.remove(userId);
                    }
                }
            }
            
            // 清理房间会话映射
            Long roomId = sessionInfo.getRoomId();
            if (roomId != null) {
                CopyOnWriteArraySet<String> roomSessionSet = roomSessions.get(roomId);
                if (roomSessionSet != null) {
                    roomSessionSet.remove(sessionId);
                    if (roomSessionSet.isEmpty()) {
                        roomSessions.remove(roomId);
                    }
                }
            }
        }
        
        // 清理Redis存储
        redisTemplate.delete("ws:session:" + sessionId);
        
        log.info("[连接管理] 移除WebSocket会话: sessionId={}", sessionId);
    }

    /**
     * 获取用户的所有会话
     */
    public CopyOnWriteArraySet<String> getUserSessions(Long userId) {
        return userSessions.getOrDefault(userId, new CopyOnWriteArraySet<>());
    }

    /**
     * 获取房间的所有会话
     */
    public CopyOnWriteArraySet<String> getRoomSessions(Long roomId) {
        return roomSessions.getOrDefault(roomId, new CopyOnWriteArraySet<>());
    }

    /**
     * 获取WebSocket会话
     */
    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 获取会话信息
     */
    public SessionInfo getSessionInfo(String sessionId) {
        return sessionInfos.get(sessionId);
    }

    /**
     * 更新会话心跳时间
     */
    public void updateHeartbeat(String sessionId) {
        SessionInfo sessionInfo = sessionInfos.get(sessionId);
        if (sessionInfo != null) {
            sessionInfo.setLastHeartbeat(LocalDateTime.now());
        }
    }

    /**
     * 获取活跃会话数量
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * 获取当前实例的连接数统计
     */
    public Map<String, Integer> getConnectionStats() {
        return Map.of(
                "totalSessions", sessions.size(),
                "totalUsers", userSessions.size(),
                "totalRooms", roomSessions.size()
        );
    }

    /**
     * 获取会话统计信息
     */
    public Map<String, Object> getSessionStats() {
        return Map.of(
                "totalSessions", sessions.size(),
                "activeUsers", userSessions.size(),
                "sessionInfos", sessionInfos.size()
        );
    }

    /**
     * 获取实例ID
     */
    private String getInstanceId() {
        // 可以使用IP+端口或其他唯一标识
        return System.getProperty("server.port", "8085") + "-" + System.currentTimeMillis();
    }

    /**
     * 会话信息内部类
     */
    @lombok.Data
    public static class SessionInfo {
        private String sessionId;
        private Long userId;
        private Long roomId;
        private LocalDateTime connectTime;
        private LocalDateTime lastHeartbeat;
        private String connectionType; // "global" 或 "room"
    }

    // ==================== 消息代理功能 (迁移自MessageBrokerService) ====================

    /**
     * 处理业务消息并分发到WebSocket连接
     * 迁移自MessageBrokerService
     */
    public void processChannelMessage(UnifiedWebSocketMessage message) {
        try {
            String channel = message.getChannel();
            String eventType = message.getEventType();
            
            if (channel == null || channel.trim().isEmpty()) {
                log.error("[消息代理] 频道名称为空，无法处理消息: messageId={}", message.getMessageId());
                return;
            }

            log.debug("[消息代理] 处理频道消息: channel={}, eventType={}, messageId={}", 
                     channel, eventType, message.getMessageId());

            // 使用频道路由
            routeMessageToChannel(channel, eventType, message);

        } catch (Exception e) {
            log.error("[消息代理] 处理频道消息失败: messageId={}, error={}", 
                    message.getMessageId(), e.getMessage(), e);
        }
    }

    /**
     * 将消息路由到特定频道
     * 迁移自MessageBrokerService
     * 
     * @param channel 频道名称
     * @param eventType 事件类型
     * @param message 消息内容
     */
    public void routeMessageToChannel(String channel, String eventType, UnifiedWebSocketMessage message) {
        try {
            // 1. 获取频道的所有订阅者
            Set<String> subscriberSessions = subscriptionManager.getChannelSubscribers(channel, eventType);
            
            if (subscriberSessions.isEmpty()) {
                log.debug("[消息代理] 频道无订阅者: channel={}, eventType={}", channel, eventType);
                return;
            }

            // 2. 批量发送消息
            int successCount = 0;
            int failCount = 0;
            
            // 记录消息发送开始
            // basicMonitor.recordMessageSent(); // Removed BasicMonitorService dependency

            for (String sessionId : subscriberSessions) {
                WebSocketSession session = getSession(sessionId);
                if (session != null && session.isOpen()) {
                    // 3. 消息过滤：检查会话是否真正订阅了该事件类型
                    if (isSessionSubscribedToChannelEvent(sessionId, channel, eventType)) {
                        sendMessageToSession(session, message);
                        successCount++;
                        
                        // 记录消息送达
                        // basicMonitor.recordMessageReceived(); // Removed BasicMonitorService dependency

                        // 4. 更新订阅活跃时间
                        SessionInfo sessionInfo = getSessionInfo(sessionId);
                        if (sessionInfo != null) {
                            subscriptionManager.updateSubscriptionActivity(sessionInfo.getUserId(), channel);
                        }
                    }
                } else {
                    failCount++;
                    log.debug("[消息代理] 会话不可用: sessionId={}", sessionId);
                }
            }

            log.debug("[消息代理] 频道消息分发完成: channel={}, eventType={}, success={}, fail={}", 
                     channel, eventType, successCount, failCount);

        } catch (Exception e) {
            log.error("[消息代理] 频道消息路由失败: channel={}, eventType={}, messageId={}", 
                     channel, eventType, message.getMessageId(), e);
        }
    }

    /**
     * 向WebSocket会话发送消息
     * 迁移自MessageBrokerService
     */
    private void sendMessageToSession(WebSocketSession session, UnifiedWebSocketMessage message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(jsonMessage));
            log.debug("[消息代理] WebSocket消息发送成功: sessionId={}, messageId={}", 
                    session.getId(), message.getMessageId());
        } catch (Exception e) {
            log.error("[消息代理] 发送WebSocket消息失败: sessionId={}, messageId={}, error={}", 
                     session.getId(), message.getMessageId(), e.getMessage(), e);
        }
    }

    /**
     * 检查会话是否订阅了特定频道事件
     * 迁移自MessageBrokerService
     */
    private boolean isSessionSubscribedToChannelEvent(String sessionId, String channel, String eventType) {
        try {
            SessionInfo sessionInfo = getSessionInfo(sessionId);
            if (sessionInfo == null) {
                return false;
            }

            // 简化版：如果用户有该频道的订阅，则认为订阅了所有事件类型
            return subscriptionManager.getUserSubscriptions(sessionInfo.getUserId())
                    .stream()
                    .anyMatch(sub -> channel.equals(sub.getChannel()));

        } catch (Exception e) {
            log.error("[消息代理] 检查会话订阅状态失败: sessionId={}, channel={}", 
                    sessionId, channel, e);
            return false;
        }
    }

    /**
     * 批量处理消息（性能优化）
     * 迁移自MessageBrokerService
     * 
     * @param messages 消息列表
     */
    public void processBatchMessages(List<UnifiedWebSocketMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        log.debug("[消息代理] 开始批量处理消息: count={}", messages.size());

        // 按频道分组消息
        Map<String, List<UnifiedWebSocketMessage>> channelGroups = new HashMap<>();
        
        for (UnifiedWebSocketMessage message : messages) {
            String channel = message.getChannel();
            if (channel != null) {
                channelGroups.computeIfAbsent(channel, k -> new ArrayList<>()).add(message);
            } else {
                // 无频道信息，跳过处理
                log.error("[消息代理] 消息缺少频道信息，跳过处理: messageId={}", message.getMessageId());
            }
        }

        // 按频道批量处理
        for (Map.Entry<String, List<UnifiedWebSocketMessage>> entry : channelGroups.entrySet()) {
            String channel = entry.getKey();
            List<UnifiedWebSocketMessage> channelMessages = entry.getValue();
            
            log.debug("[消息代理] 处理频道批量消息: channel={}, count={}", channel, channelMessages.size());
            
            for (UnifiedWebSocketMessage message : channelMessages) {
                processChannelMessage(message);
            }
        }

        log.debug("[消息代理] 批量消息处理完成: totalCount={}, channels={}", 
                messages.size(), channelGroups.size());
    }
} 