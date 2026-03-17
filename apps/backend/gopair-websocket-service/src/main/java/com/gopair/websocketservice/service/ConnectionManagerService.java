package com.gopair.websocketservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionManagerService {

    private static final long SESSION_TTL_SECONDS = 600L;

    private final SessionStore sessionStore;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void addGlobalSession(WebSocketSession session, Long userId) {
        String sessionId = session.getId();

        sessions.put(sessionId, session);
        String instanceId = System.getProperty("server.port", "8085") + "-" + System.currentTimeMillis();

        // 使用 SessionStore 统一维护会话与用户索引
        sessionStore.saveSession(sessionId, userId, "global", null, instanceId, SESSION_TTL_SECONDS);
        sessionStore.addUserSession(userId, sessionId);
        
        log.info("[连接管理] 添加全局WebSocket会话: sessionId={}, userId={}", sessionId, userId);
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);

        // 从 Redis 中读取会话信息以便清理用户会话索引
        Map<Object, Object> redisInfo = sessionStore.getSession(sessionId);
        Object userIdObj = redisInfo.get("userId");
        if (userIdObj != null) {
            try {
                Long userId = Long.valueOf(userIdObj.toString());
                sessionStore.removeUserSession(userId, sessionId);
            } catch (NumberFormatException ex) {
                log.warn("[连接管理] 解析会话用户ID失败，跳过用户会话索引清理: sessionId={}, userId={}", sessionId, userIdObj);
            }
        }

        // 删除 Redis 中的会话信息
        sessionStore.removeSession(sessionId);
        log.info("[连接管理] 移除WebSocket会话: sessionId={}", sessionId);
    }

    public Set<String> getUserSessions(Long userId) {
        // 在线会话视图以 Redis 为准
        return sessionStore.getUserSessions(userId);
    }

    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public SessionInfo getSessionInfo(String sessionId) {
        Map<Object, Object> redisInfo = sessionStore.getSession(sessionId);
        if (redisInfo.isEmpty()) {
            log.debug("[连接管理] 会话信息不存在: sessionId={}", sessionId);
            return null;
        }

        SessionInfo sessionInfo = new SessionInfo();
        sessionInfo.setSessionId(sessionId);

        Object userId = redisInfo.get("userId");
        if (userId != null) {
            try {
                sessionInfo.setUserId(Long.valueOf(userId.toString()));
            } catch (NumberFormatException ex) {
                log.warn("[连接管理] 解析会话用户ID失败: sessionId={}, userId={}", sessionId, userId);
            }
        }

        Object roomId = redisInfo.get("roomId");
        if (roomId != null) {
            String roomIdStr = roomId.toString();
            if (!roomIdStr.isEmpty()) {
                try {
                    sessionInfo.setRoomId(Long.valueOf(roomIdStr));
                } catch (NumberFormatException ex) {
                    log.warn("[连接管理] 解析会话房间ID失败: sessionId={}, roomId={}", sessionId, roomIdStr);
                }
            }
        }

        Object connectionType = redisInfo.get("connectionType");
        if (connectionType != null) {
            sessionInfo.setConnectionType(connectionType.toString());
        }

        Object connectTime = redisInfo.get("connectTime");
        if (connectTime != null) {
            try {
                sessionInfo.setConnectTime(LocalDateTime.parse(connectTime.toString()));
            } catch (Exception ex) {
                log.warn("[连接管理] 解析会话连接时间失败: sessionId={}, connectTime={}", sessionId, connectTime);
            }
        }

        Object lastActiveTime = redisInfo.get("lastActiveTime");
        if (lastActiveTime != null) {
            try {
                sessionInfo.setLastHeartbeat(LocalDateTime.parse(lastActiveTime.toString()));
            } catch (Exception ex) {
                log.warn("[连接管理] 解析会话最近活跃时间失败: sessionId={}, lastActiveTime={}", sessionId, lastActiveTime);
            }
        }

        return sessionInfo;
    }

    public void updateHeartbeat(String sessionId) {
        // 直接刷新 Redis 中会话的 TTL 与最近活跃时间
        sessionStore.refreshSessionTtl(sessionId, SESSION_TTL_SECONDS);
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    public Map<String, Integer> getConnectionStats() {
        return Map.of("totalSessions", sessions.size());
    }

    public Map<String, Object> getSessionStats() {
        return Map.of("totalSessions", sessions.size());
    }

    @lombok.Data
    public static class SessionInfo {
        private String sessionId;
        private Long userId;
        private Long roomId;
        private LocalDateTime connectTime;
        private LocalDateTime lastHeartbeat;
        private String connectionType;
    }
}
