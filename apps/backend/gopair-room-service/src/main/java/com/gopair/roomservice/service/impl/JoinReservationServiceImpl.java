package com.gopair.roomservice.service.impl;

import com.gopair.framework.logging.annotation.LogRecord;
import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.domain.event.JoinRoomRequestedEvent;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.messaging.JoinRoomProducer;
import com.gopair.roomservice.service.JoinReservationService;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 加入预约服务实现，基于 Redis Lua 脚本保证预占操作的原子性。
 * 流程：生成 token → 执行 Lua 脚本做预占（扣减名额、写 pending） → 发送 MQ 消息 → 返回结果
 */
@Service
@Slf4j
public class JoinReservationServiceImpl implements JoinReservationService {

    private final StringRedisTemplate stringRedisTemplate;
    /** 预占房间的 Lua 脚本，保证检查名额与写入 pending 的原子性 */
    private final DefaultRedisScript<Long> roomPreReserveScript;
    /** 预占回滚的 Lua 脚本，当 MQ 发送失败时撤销预占 */
    private final DefaultRedisScript<Long> roomRollbackReserveScript;
    private final JoinRoomProducer joinRoomProducer;
    private final RoomMapper roomMapper;

    /** token 有效期（秒），超时未完成加入则 token 失效 */
    @Value("${gopair.room.reservation.join-token-ttl-seconds:30}")
    private long tokenTtlSeconds;

    public JoinReservationServiceImpl(StringRedisTemplate stringRedisTemplate,
                                      DefaultRedisScript<Long> roomPreReserveScript,
                                      DefaultRedisScript<Long> roomRollbackReserveScript,
                                      JoinRoomProducer joinRoomProducer,
                                      RoomMapper roomMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.roomPreReserveScript = roomPreReserveScript;
        this.roomRollbackReserveScript = roomRollbackReserveScript;
        this.joinRoomProducer = joinRoomProducer;
        this.roomMapper = roomMapper;
    }

    /**
     * 预占房间核心逻辑。
     *
     * 完整流程：
     * 1. 构建 Redis key（meta/members/pending/token）确保房间元数据已在 Redis 中初始化（不存在时从 DB 加载）
     * 2. 执行 Lua 脚本进行名额预占（原子判断：已加入/已满/已关闭/过期 → 写入 pending）
     * 3. 预占成功则发送 MQ 消息；若发送失败则回滚预占
     * 4. 返回预占结果（状态 + token + 消息）
     *
     * @see JoinReservationService#preReserve(Long, Long)
     */
    @Override
    @LogRecord(operation = "预占房间名额", module = "加入预约")
    public PreReserveResult preReserve(Long roomId, Long userId) {
        String token      = UUID.randomUUID().toString().replace("-", "");
        String metaKey    = RoomConst.metaKey(roomId);
        String membersKey = RoomConst.membersKey(roomId);
        String pendingKey = RoomConst.pendingKey(roomId);
        String tokenKey   = RoomConst.joinTokenKey(token);

        ensureRoomMetaInitialized(roomId);

        if (log.isDebugEnabled()) {
            RoomRedisDiagnostics snapshot = snapshotRoomState(roomId);
            log.debug("[房间服务][join-async] 预占开始 房间={} 用户={} meta={} pendingKeys={} membersSize={}",
                    roomId, userId,
                    snapshot.getMeta(), snapshot.getPending().keySet(), snapshot.getMembers().size());
        }

        Long res = stringRedisTemplate.execute(
                roomPreReserveScript,
                Arrays.asList(metaKey, membersKey, pendingKey, tokenKey),
                String.valueOf(userId),
                token,
                String.valueOf(Instant.now().toEpochMilli()),
                String.valueOf(tokenTtlSeconds)
        );
        if (res == null) {
            return PreReserveResult.of(ReserveStatus.SYSTEM_BUSY, null, "系统繁忙，请重试");
        }
        ReserveStatus status;
        switch (res.intValue()) {
            case 0: status = ReserveStatus.ACCEPTED;        break;
            case 1: status = ReserveStatus.ALREADY_JOINED;   break;
            case 2: status = ReserveStatus.FULL;             break;
            case 3: status = ReserveStatus.CLOSED;           break;
            case 4: status = ReserveStatus.EXPIRED;         break;
            case 5: status = ReserveStatus.ALREADY_PROCESSING; break;
            default: status = ReserveStatus.SYSTEM_BUSY;
        }
        if (log.isDebugEnabled()) {
            RoomRedisDiagnostics snapshot = snapshotRoomState(roomId);
            log.debug("[房间服务][join-async] 预占完成 房间={} 用户={} token={} 状态={} meta={} reserved={} pending={}",
                    roomId, userId, token, status,
                    snapshot.getMeta(), snapshot.getMeta().getOrDefault(RoomConst.FIELD_RESERVED, ""), snapshot.getPending());
        }
        if (status == ReserveStatus.ACCEPTED) {
            JoinRoomRequestedEvent event = new JoinRoomRequestedEvent(roomId, userId, token, System.currentTimeMillis());
            boolean sent = joinRoomProducer.sendRequested(event);
            if (!sent) {
                stringRedisTemplate.execute(
                        roomRollbackReserveScript,
                        Arrays.asList(metaKey, pendingKey),
                        String.valueOf(userId),
                        tokenKey
                );
                return PreReserveResult.of(ReserveStatus.SYSTEM_BUSY, null, "系统繁忙，请重试");
            }
            return PreReserveResult.of(status, token, "已受理");
        }
        if (status == ReserveStatus.SYSTEM_BUSY) {
            RoomRedisDiagnostics snapshot = snapshotRoomState(roomId);
            log.warn("[房间服务][join-async] 预占返回处理中 房间={} 用户={} token={} meta={} pending={} members={}",
                    roomId, userId, token, snapshot.getMeta(), snapshot.getPending(), snapshot.getMembers());
        }
        return PreReserveResult.of(status, null, status.name());
    }

    /**
     * 确保房间元数据已在 Redis 中初始化。
     *
     * 逻辑说明：
     * - 先检查 metaKey 是否已存在 FIELD_MAX 字段（已有初始化则跳过）
     * - 若未初始化，抢占分布式锁后从 DB 查询房间信息，构建完整的元数据字段并写入 Redis
     * - 未抢到锁的请求等待 50ms 后重试（此时缓存大概率已由抢到锁的线程写入）
     * - 双重检查：抢到锁后再次检查 metaKey，避免重复查 DB
     *
     * @param roomId 房间 ID
     */
    private void ensureRoomMetaInitialized(Long roomId) {
        String metaKey = RoomConst.metaKey(roomId);
        Boolean hasMax = stringRedisTemplate.opsForHash().hasKey(metaKey, RoomConst.FIELD_MAX);
        if (hasMax != null && hasMax) {
            return;
        }

        String lockKey = RoomConst.metaInitLockKey(roomId);
        // 抢占分布式锁，TTL 5s 防止死锁（如进程崩溃未释放）
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", 5L, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(locked)) {
            try {
                // 双重检查：抢到锁后再次确认，可能有其他请求刚写完
                hasMax = stringRedisTemplate.opsForHash().hasKey(metaKey, RoomConst.FIELD_MAX);
                if (hasMax != null && hasMax) {
                    return;
                }

                Room room = roomMapper.selectById(roomId);
                if (room == null) {
                    return;
                }
                String pendingKey = RoomConst.pendingKey(roomId);
                Long pendingCount = stringRedisTemplate.opsForHash().size(pendingKey);
                long reserved = (pendingCount != null && pendingCount > 0) ? pendingCount : 0L;
                long expireAtMs = room.getExpireTime() == null ? 0L
                        : room.getExpireTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                int passwordMode = room.getPasswordMode() == null ? RoomConst.PASSWORD_MODE_NONE : room.getPasswordMode();
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put(RoomConst.FIELD_MAX,           String.valueOf(room.getMaxMembers()     == null ? 0 : room.getMaxMembers()));
                fields.put(RoomConst.FIELD_CONFIRMED,     String.valueOf(room.getCurrentMembers() == null ? 0 : room.getCurrentMembers()));
                fields.put(RoomConst.FIELD_RESERVED,      String.valueOf(reserved));
                fields.put(RoomConst.FIELD_STATUS,        String.valueOf(room.getStatus()         == null ? RoomConst.STATUS_ACTIVE : room.getStatus()));
                fields.put(RoomConst.FIELD_EXPIRE_AT,     String.valueOf(expireAtMs));
                fields.put(RoomConst.FIELD_PASSWORD_MODE, String.valueOf(passwordMode));
                stringRedisTemplate.opsForHash().putAll(metaKey, fields);
                if (log.isDebugEnabled()) {
                    log.debug("[房间服务][join-async] 房间={} 初始化缓存元数据 reserved={}(from pending) meta={}",
                            roomId, reserved, fields);
                }
            } finally {
                stringRedisTemplate.delete(lockKey);
            }
        } else {
            // 未抢到锁：等待其他请求完成，再重试读缓存
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {}
            // 50ms 后再次检查，缓存大概率已由抢到锁的线程写入
            hasMax = stringRedisTemplate.opsForHash().hasKey(metaKey, RoomConst.FIELD_MAX);
            if (hasMax == null || !hasMax) {
                // 兜底：仍未初始化，递归重试一次（最多一次，防止极端情况）
                ensureRoomMetaInitialized(roomId);
            }
        }
    }

    /**
     * 快照当前时刻 Redis 中房间的完整状态，用于诊断和日志。
     * 读取三个关键数据结构：meta（房间元数据）、pending（待确认预占）、members（已确认成员）
     *
     * @param roomId 房间 ID
     * @return 包含 meta/pending/members 三部分的快照对象，读取失败时返回空集合
     */
    RoomRedisDiagnostics snapshotRoomState(Long roomId) {
        try {
            Map<String, String> meta    = readHash(RoomConst.metaKey(roomId));
            Map<String, String> pending = readHash(RoomConst.pendingKey(roomId));
            Set<String> members         = readSet(RoomConst.membersKey(roomId));
            return new RoomRedisDiagnostics(meta, pending, members);
        } catch (Exception e) {
            log.warn("[房间服务][join-async] 获取房间={} Redis快照失败", roomId, e);
            return new RoomRedisDiagnostics(Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet());
        }
    }

    /**
     * 从 Redis Hash 中读取所有 field-value 对，以 String-String Map 返回。
     *
     * @param key Redis Hash 的 key
     * @return 所有字段的键值对
     */
    private Map<String, String> readHash(String key) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        Map<String, String> result = new HashMap<>();
        entries.forEach((k, v) -> result.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
        return result;
    }

    /**
     * 从 Redis Set 中读取所有成员，以 String Set 返回。
     *
     * @param key Redis Set 的 key
     * @return 所有成员 ID 集合，空时返回空集合
     */
    private Set<String> readSet(String key) {
        Set<String> members = stringRedisTemplate.opsForSet().members(key);
        return members == null ? Collections.emptySet() : members;
    }

    /**
     * 房间 Redis 快照数据类，用于诊断日志。
     * 记录预占前后 Redis 中关键数据结构的实时状态。
     */
    @Getter
    @ToString
    static class RoomRedisDiagnostics {
        /** 房间元数据 Hash：max、confirmed、reserved、status、expireAt、passwordMode */
        private final Map<String, String> meta;
        /** 待确认预占 Hash：userId → token */
        private final Map<String, String> pending;
        /** 已确认成员 Set：userId 集合 */
        private final Set<String> members;

        RoomRedisDiagnostics(Map<String, String> meta, Map<String, String> pending, Set<String> members) {
            this.meta    = meta;
            this.pending = pending;
            this.members = members;
        }
    }
}
