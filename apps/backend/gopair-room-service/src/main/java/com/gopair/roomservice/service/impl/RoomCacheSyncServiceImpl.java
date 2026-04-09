package com.gopair.roomservice.service.impl;

import com.gopair.framework.logging.annotation.LogRecord;
import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.service.RoomCacheSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.ZoneId;

@Slf4j
@Service
public class RoomCacheSyncServiceImpl implements RoomCacheSyncService {

    private final StringRedisTemplate redis;

    public RoomCacheSyncServiceImpl(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    @LogRecord(operation = "初始化房间缓存", module = "缓存同步")
    public void initializeRoomInCache(Room room, Long ownerId) {
        if (room == null || room.getRoomId() == null) return;
        Long roomId = room.getRoomId();
        String meta = RoomConst.metaKey(roomId);
        String expireAt = String.valueOf(
                room.getExpireTime() == null ? 0L
                        : room.getExpireTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        try {
            redis.opsForHash().put(meta, RoomConst.FIELD_MAX,
                    String.valueOf(room.getMaxMembers() == null ? 0 : room.getMaxMembers()));
            redis.opsForHash().put(meta, RoomConst.FIELD_CONFIRMED,
                    String.valueOf(room.getCurrentMembers() == null ? 0 : room.getCurrentMembers()));
            redis.opsForHash().put(meta, RoomConst.FIELD_RESERVED, "0");
            redis.opsForHash().put(meta, RoomConst.FIELD_STATUS,
                    String.valueOf(room.getStatus() == null ? RoomConst.STATUS_ACTIVE : room.getStatus()));
            redis.opsForHash().put(meta, RoomConst.FIELD_EXPIRE_AT, expireAt);
            // 密码模式写入缓存，供入房预检使用
            int passwordMode = room.getPasswordMode() == null ? RoomConst.PASSWORD_MODE_NONE : room.getPasswordMode();
            redis.opsForHash().put(meta, RoomConst.FIELD_PASSWORD_MODE, String.valueOf(passwordMode));
            if (ownerId != null) {
                redis.opsForHash().put(meta, RoomConst.FIELD_OWNER_ID, String.valueOf(ownerId));
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
            log.debug("[房间服务] Redis 添加成员失败 roomId={} userId={} 错误={}", roomId, userId, e.getMessage());
        }
    }

    @Override
    @LogRecord(operation = "从缓存移除成员", module = "缓存同步")
    public void removeMemberFromCache(Long roomId, Long userId) {
        try {
            redis.opsForSet().remove(RoomConst.membersKey(roomId), String.valueOf(userId));
        } catch (Exception e) {
            log.debug("[房间服务] Redis 移除成员失败 roomId={} userId={} 错误={}", roomId, userId, e.getMessage());
        }
    }

    @Override
    @LogRecord(operation = "更新确认成员数", module = "缓存同步")
    public void incrementConfirmed(Long roomId, int delta) {
        try {
            redis.opsForHash().increment(RoomConst.metaKey(roomId), RoomConst.FIELD_CONFIRMED, delta);
        } catch (Exception e) {
            log.debug("[房间服务] Redis 更新确认成员数失败 roomId={} delta={} 错误={}", roomId, delta, e.getMessage());
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
}
