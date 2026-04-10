package com.gopair.websocketservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    /**
     * 移除会话并返回 userId，减少一次 Redis 往返。
     *
     * 适用于 afterConnectionClosed 回调场景：此时 WebSocket 连接已断开，
     * session 的 Redis key 可能已因 TTL 过期被 Redis 自动清除，
     * 若先读后删则 userId 读不到。通过在一次 Redis 操作中完成读取+删除，
     * 即使 key 恰好在读取后被删除，userId 仍能获取。
     *
     * @param sessionId 会话 ID
     * @return 用户的 ID，获取失败时返回 null
     */
    public Long removeSessionAndGetUserId(String sessionId) {
        sessions.remove(sessionId);

        Map<Object, Object> redisInfo = sessionStore.getSession(sessionId);
        Object userIdObj = redisInfo.get("userId");

        sessionStore.removeSession(sessionId);

        if (userIdObj != null) {
            try {
                Long userId = Long.valueOf(userIdObj.toString());
                sessionStore.removeUserSession(userId, sessionId);
                log.info("[连接管理] 移除WebSocket会话: sessionId={}, userId={}", sessionId, userId);
                return userId;
            } catch (NumberFormatException ex) {
                log.warn("[连接管理] 解析会话用户ID失败，跳过用户会话索引清理: sessionId={}, userId={}", sessionId, userIdObj);
            }
        }
        log.info("[连接管理] 移除WebSocket会话: sessionId={}", sessionId);
        return null;
    }

    public Set<String> getUserSessions(Long userId) {
        // 在线会话视图以 Redis 为准
        return sessionStore.getUserSessions(userId);
    }

    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public SessionInfo getSessionInfo(String sessionId) {
        Map<Object, Object> redisInfo;
        try {
            redisInfo = sessionStore.getSession(sessionId);
        } catch (Exception e) {
            log.warn("[连接管理] 获取会话信息失败: sessionId={}, error={}", sessionId, e.getMessage());
            return null;
        }
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
            sessionInfo.setLastHeartbeat(parseLastActiveTime(lastActiveTime));
        }

        return sessionInfo;
    }

    public void updateHeartbeat(String sessionId) {
        // 直接刷新 Redis 中会话的 TTL 与最近活跃时间
        sessionStore.refreshSessionTtl(sessionId, SESSION_TTL_SECONDS);
    }

    /**
     * 获取会话最近活跃时间，用于心跳超时检测。
     *
     * @param sessionId 会话 ID
     * @return 最近活跃时间，读取失败时返回 null
     */
    public LocalDateTime getSessionLastActiveTime(String sessionId) {
        Map<Object, Object> redisInfo;
        try {
            redisInfo = sessionStore.getSession(sessionId);
        } catch (Exception e) {
            log.warn("[连接管理] 获取会话活跃时间失败: sessionId={}, error={}", sessionId, e.getMessage());
            return null;
        }
        if (redisInfo.isEmpty()) {
            return null;
        }
        Object lastActiveTime = redisInfo.get("lastActiveTime");
        if (lastActiveTime == null) {
            return null;
        }
        return parseLastActiveTime(lastActiveTime);
    }

    /**
     * 解析 lastActiveTime，统一返回 UTC epoch 秒对应的 LocalDateTime。
     * - 纯数字：UTC epoch 秒，直接转换
     * - ISO 字符串：假设为 UTC 格式（ISO 格式固定为 UTC），直接解析
     *
     * 注意：不要传入北京时间 ISO 字符串（如 2026-04-11T18:30:00），
     * 因为 LocalDateTime.parse 默认不带时区，会被错误地当作 UTC 处理，
     * 导致 8 小时偏移。所有时间统一以 epoch 秒存储。
     *
     * @param lastActiveTime Redis 中的原始值（epoch 秒数字或 ISO 字符串）
     */
    private LocalDateTime parseLastActiveTime(Object lastActiveTime) {
        String str = lastActiveTime.toString();
        try {
            long epochSecond = Long.parseLong(str);
            return LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC);
        } catch (NumberFormatException _) {
            // ISO 格式（假设为 UTC，如 2026-04-11T10:30:00Z）
            return LocalDateTime.parse(str);
        }
    }

    /**
     * 返回本地内存中所有会话 ID，用于心跳超时定时扫描。
     */
    public Set<String> getAllSessionIds() {
        return new HashSet<>(sessions.keySet());
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
