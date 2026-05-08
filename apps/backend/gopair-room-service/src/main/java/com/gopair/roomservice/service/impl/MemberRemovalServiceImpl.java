package com.gopair.roomservice.service.impl;

import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.service.MemberRemovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * 成员移除服务实现，基于 Redis Lua 脚本保证操作的原子性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberRemovalServiceImpl implements MemberRemovalService {

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> roomMarkRemovalScript;
    private final DefaultRedisScript<Long> roomRollbackRemovalScript;

    /** pending_removal 键的 TTL（秒），防止 Redis 堆积 */
    @Value("${gopair.room.removal-pending-ttl-seconds:300}")
    private long pendingTtlSeconds;

    @Override
    public boolean markRemovalPending(Long roomId, Long userId, int leaveType) {
        Long res = stringRedisTemplate.execute(
                roomMarkRemovalScript,
                Arrays.asList(
                        RoomConst.metaKey(roomId),
                        RoomConst.membersKey(roomId),
                        RoomConst.pendingRemovalKey(roomId)
                ),
                String.valueOf(userId),
                String.valueOf(leaveType),
                String.valueOf(pendingTtlSeconds)
        );
        if (res == null) {
            log.warn("[房间服务][removal] Lua 脚本执行返回 null roomId={} userId={}", roomId, userId);
            return false;
        }
        boolean success = res == 0L;
        log.info("[房间服务][removal] 标记移除 roomId={} userId={} leaveType={} success={}",
                roomId, userId, leaveType, success);
        return success;
    }

    @Override
    public void rollbackRemoval(Long roomId, Long userId) {
        Long res = stringRedisTemplate.execute(
                roomRollbackRemovalScript,
                Arrays.asList(
                        RoomConst.metaKey(roomId),
                        RoomConst.membersKey(roomId),
                        RoomConst.pendingRemovalKey(roomId)
                ),
                String.valueOf(userId)
        );
        log.info("[房间服务][removal] 回滚移除 roomId={} userId={} rolledBack={}",
                roomId, userId, res != null && res == 1L);
    }
}
