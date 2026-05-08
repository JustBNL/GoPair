package com.gopair.roomservice.service;

/**
 * 加入预约服务，负责处理用户进入房间前的预占逻辑。
 * 通过 Redis Lua 脚本实现原子性操作，避免并发冲突。
 */
public interface JoinReservationService {

    /**
     * 预占结果状态枚举
     */
    enum ReserveStatus {
        ACCEPTED,       // 预占成功，可继续加入
        ALREADY_JOINED,  // 用户已在房间中（正式成员）
        ALREADY_PROCESSING, // 用户已在 pending 中（已有预占未完成）
        FULL,           // 房间人数已满
        CLOSED,         // 房间已关闭
        ARCHIVED,       // 房间已归档
        EXPIRED,        // 房间已过期（expireAt 边界情况，由 Java 侧定期修正）
        SYSTEM_BUSY     // 系统繁忙（Redis 超时 / MQ 发送失败），稍后重试
    }

    /**
     * 预占结果封装类，包含状态、token 和提示消息
     */
    class PreReserveResult {
        public ReserveStatus status;
        public String joinToken;  // 预占成功后返回的加入凭证
        public String message;    // 给用户的提示

        public PreReserveResult(ReserveStatus status, String joinToken, String message) {
            this.status = status;
            this.joinToken = joinToken;
            this.message = message;
        }

        public static PreReserveResult of(ReserveStatus status, String token, String msg) {
            return new PreReserveResult(status, token, msg);
        }
    }

    /**
     * 预占房间入口方法。
     * 在用户正式加入房间前，先在 Redis 中预留一个位置（pending），生成唯一的 joinToken，
     * 并向消息队列发送异步加入请求。
     *
     * @param roomId      房间 ID
     * @param userId      用户 ID
     * @return 预占结果，包含状态、token（成功时）和消息
     */
    PreReserveResult preReserve(Long roomId, Long userId);
} 