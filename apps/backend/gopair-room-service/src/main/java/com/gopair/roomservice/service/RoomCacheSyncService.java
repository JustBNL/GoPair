package com.gopair.roomservice.service;

import com.gopair.roomservice.domain.po.Room;

public interface RoomCacheSyncService {
    void initializeRoomInCache(Room room, Long ownerId);
    void addMemberToCache(Long roomId, Long userId);
    void removeMemberFromCache(Long roomId, Long userId);
    void incrementConfirmed(Long roomId, int delta);
    void setStatus(Long roomId, int status);
} 