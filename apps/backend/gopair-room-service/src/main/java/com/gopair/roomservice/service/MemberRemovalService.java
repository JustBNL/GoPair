package com.gopair.roomservice.service;

/**
 * 成员移除服务，提供 Redis Lua 原子操作入口。
 */
public interface MemberRemovalService {

    /**
     * 执行 Lua 脚本做原子移除预标记。
     *
     * @param roomId    房间ID
     * @param userId    被移除的用户ID
     * @param leaveType 离开类型，见 {@link com.gopair.roomservice.enums.LeaveTypeEnum}
     * @return true=标记成功（用户在房间中）；false=用户不在房间
     */
    boolean markRemovalPending(Long roomId, Long userId, int leaveType);

    /**
     * MQ 发送失败时回滚移除标记（恢复 Redis 状态）。
     *
     * @param roomId 房间ID
     * @param userId 用户ID
     */
    void rollbackRemoval(Long roomId, Long userId);
}
