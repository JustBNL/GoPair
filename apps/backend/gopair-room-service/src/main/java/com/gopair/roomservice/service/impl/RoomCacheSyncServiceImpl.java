package com.gopair.roomservice.service.impl;

import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.service.RoomCacheSyncService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.ZoneId;

@Service
public class RoomCacheSyncServiceImpl implements RoomCacheSyncService {

    private final StringRedisTemplate redis;

    public RoomCacheSyncServiceImpl(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String metaKey(Long roomId) { return "room:" + roomId + ":meta"; }
    private String membersKey(Long roomId) { return "room:" + roomId + ":members"; }

    @Override
    public void initializeRoomInCache(Room room, Long ownerId) {
        if (room == null || room.getRoomId() == null) return;
        Long roomId = room.getRoomId();
        String meta = metaKey(roomId);
        String expireAt = String.valueOf(
                room.getExpireTime() == null ? 0L
                        : room.getExpireTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        try {
            redis.opsForHash().put(meta, "max", String.valueOf(room.getMaxMembers() == null ? 0 : room.getMaxMembers()));
            redis.opsForHash().put(meta, "confirmed", String.valueOf(room.getCurrentMembers() == null ? 0 : room.getCurrentMembers()));
            redis.opsForHash().put(meta, "reserved", "0");
            redis.opsForHash().put(meta, "status", String.valueOf(room.getStatus() == null ? 0 : room.getStatus()));
            redis.opsForHash().put(meta, "expireAt", expireAt);
            // 密码模式写入缓存，供入房预检使用
            int passwordMode = room.getPasswordMode() == null ? 0 : room.getPasswordMode();
            redis.opsForHash().put(meta, "passwordMode", String.valueOf(passwordMode));
            if (ownerId != null) {
                redis.opsForHash().put(meta, "ownerId", String.valueOf(ownerId));
                redis.opsForSet().add(membersKey(roomId), String.valueOf(ownerId));
            }
        } catch (Exception ignore) {
        }
    }

    @Override
    public void addMemberToCache(Long roomId, Long userId) {
        try { redis.opsForSet().add(membersKey(roomId), String.valueOf(userId)); } catch (Exception ignore) {}
    }

    @Override
    public void removeMemberFromCache(Long roomId, Long userId) {
        try { redis.opsForSet().remove(membersKey(roomId), String.valueOf(userId)); } catch (Exception ignore) {}
    }

    @Override
    public void incrementConfirmed(Long roomId, int delta) {
        try { redis.opsForHash().increment(metaKey(roomId), "confirmed", delta); } catch (Exception ignore) {}
    }

    @Override
    public void setStatus(Long roomId, int status) {
        try { redis.opsForHash().put(metaKey(roomId), "status", String.valueOf(status)); } catch (Exception ignore) {}
    }

    @Override
    public void setPasswordMode(Long roomId, int passwordMode) {
        try { redis.opsForHash().put(metaKey(roomId), "passwordMode", String.valueOf(passwordMode)); } catch (Exception ignore) {}
    }
}
