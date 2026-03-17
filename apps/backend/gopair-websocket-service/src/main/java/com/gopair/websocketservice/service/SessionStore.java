package com.gopair.websocketservice.service;

import java.util.Map;
import java.util.Set;

/**
 * 会话存储接口，抽象出基于 Redis 的会话读写能力。
 *
 * 仅包含当前 WebSocket 服务实际使用的会话相关操作，
 * 方便上层按最小依赖进行注入，降低对具体实现的耦合。
 */
public interface SessionStore {

    void saveSession(String sessionId,
                     Long userId,
                     String connectionType,
                     Long roomId,
                     String instanceId,
                     long ttlSeconds);

    void refreshSessionTtl(String sessionId, long ttlSeconds);

    void removeSession(String sessionId);

    Map<Object, Object> getSession(String sessionId);

    void addUserSession(Long userId, String sessionId);

    void removeUserSession(Long userId, String sessionId);

    Set<String> getUserSessions(Long userId);
}

