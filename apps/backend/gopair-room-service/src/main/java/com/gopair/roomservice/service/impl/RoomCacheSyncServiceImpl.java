package com.gopair.roomservice.service.impl;

import com.gopair.framework.logging.annotation.LogRecord;
import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.service.RoomCacheSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class RoomCacheSyncServiceImpl implements RoomCacheSyncService {

    private final StringRedisTemplate redis;

    public RoomCacheSyncServiceImpl(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 初始化房间元信息到 Redis Hash，结构如下：
     *
     *   Key:   room:meta:{roomId}
     *   Type:  Hash
     *   Fields:
     *     max          -> 房间最大人数（Integer）
     *     confirmed    -> 已确认人数（Integer）
     *     reserved    -> 待确认预占数（Integer）
     *     status       -> 房间状态（Integer）
     *     expireAt     -> 过期时间戳毫秒（Long）
     *     passwordMode -> 密码模式（Integer, 0=无密码）
     *     ownerId      -> 房主用户ID（Long，仅 ownerId != null 时写入）
     *
     *   Key:   room:members:{roomId}
     *   Type:  Set
     *   Value: {ownerId}（仅 ownerId != null 时写入）
     */
    @Override
    @LogRecord(operation = "初始化房间缓存", module = "缓存同步")
    public void initializeRoomInCache(Room room, Long ownerId) {
        if (room == null || room.getRoomId() == null) return;

        Long roomId = room.getRoomId();
        String meta = RoomConst.metaKey(roomId);
        long expireAtMs = room.getExpireTime() == null ? 0L
                : room.getExpireTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        int passwordMode = room.getPasswordMode() == null ? RoomConst.PASSWORD_MODE_NONE : room.getPasswordMode();

        Map<String, String> fields = new HashMap<>();
        fields.put(RoomConst.FIELD_MAX,          String.valueOf(room.getMaxMembers()     == null ? 0 : room.getMaxMembers()));
        fields.put(RoomConst.FIELD_CONFIRMED,   String.valueOf(room.getCurrentMembers() == null ? 0 : room.getCurrentMembers()));
        fields.put(RoomConst.FIELD_RESERVED,     "0");
        fields.put(RoomConst.FIELD_STATUS,       String.valueOf(room.getStatus()         == null ? RoomConst.STATUS_ACTIVE : room.getStatus()));
        fields.put(RoomConst.FIELD_EXPIRE_AT,    String.valueOf(expireAtMs));
        fields.put(RoomConst.FIELD_PASSWORD_MODE, String.valueOf(passwordMode));
        if (ownerId != null) {
            fields.put(RoomConst.FIELD_OWNER_ID, String.valueOf(ownerId));
        }
        try {
            redis.opsForHash().putAll(meta, fields);
            if (ownerId != null) {
                redis.opsForSet().add(RoomConst.membersKey(roomId), String.valueOf(ownerId));
            }
            log.info("[房间服务] Redis 缓存初始化成功 roomId={}", roomId);
        } catch (Exception e) {
            log.warn("[房间服务] Redis 缓存初始化失败 roomId={} 错误={}，不影响主流程", roomId, e.getMessage());
        }
    }

    @Override
    @LogRecord(operation = "添加成员到缓存", module = "缓存同步")
    public void addMemberToCache(Long roomId, Long userId) {
        try {
            redis.opsForSet().add(RoomConst.membersKey(roomId), String.valueOf(userId));
        } catch (Exception e) {
            log.warn("[房间服务] Redis 添加成员失败 roomId={} userId={} 错误={}", roomId, userId, e.getMessage());
        }
    }

    @Override
    @LogRecord(operation = "从缓存移除成员", module = "缓存同步")
    public void removeMemberFromCache(Long roomId, Long userId) {
        try {
            redis.opsForSet().remove(RoomConst.membersKey(roomId), String.valueOf(userId));
        } catch (Exception e) {
            log.warn("[房间服务] Redis 移除成员失败 roomId={} userId={} 错误={}", roomId, userId, e.getMessage());
        }
    }

    @Override
    @LogRecord(operation = "更新确认成员数", module = "缓存同步")
    public void incrementConfirmed(Long roomId, int delta) {
        try {
            redis.opsForHash().increment(RoomConst.metaKey(roomId), RoomConst.FIELD_CONFIRMED, delta);
        } catch (Exception e) {
            log.warn("[房间服务] Redis 更新确认成员数失败 roomId={} delta={} 错误={}，缓存与DB可能不一致", roomId, delta, e.getMessage());
        }
    }

    @Override
    @LogRecord(operation = "更新缓存房间状态", module = "缓存同步")
    public void setStatus(Long roomId, int status) {
        try {
             redis.opsForHash().put(RoomConst.metaKey(roomId), RoomConst.FIELD_STATUS, String.valueOf(status));
        } catch (Exception e) {
            log.warn("[房间服务] Redis 更新房间状态失败 roomId={} status={} 错误={}", roomId, status, e.getMessage());
        }
    }

    @Override
    @LogRecord(operation = "更新缓存密码模式", module = "缓存同步")
    public void setPasswordMode(Long roomId, int passwordMode) {
        try {
            redis.opsForHash().put(RoomConst.metaKey(roomId), RoomConst.FIELD_PASSWORD_MODE, String.valueOf(passwordMode));
        } catch (Exception e) {
            log.warn("[房间服务] Redis 更新密码模式失败 roomId={} passwordMode={} 错误={}", roomId, passwordMode, e.getMessage());
        }
    }

    @Override
    @LogRecord(operation = "更新缓存过期时间", module = "缓存同步")
    public void setExpireAt(Long roomId, long expireAtMs) {
        try {
            redis.opsForHash().put(RoomConst.metaKey(roomId), RoomConst.FIELD_EXPIRE_AT, String.valueOf(expireAtMs));
        } catch (Exception e) {
            log.warn("[房间服务] Redis 更新过期时间失败 roomId={} expireAtMs={} 错误={}", roomId, expireAtMs, e.getMessage());
        }
    }
}
