package com.gopair.roomservice.service.impl;

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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class JoinReservationServiceImpl implements JoinReservationService {

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> roomPreReserveScript;
    private final DefaultRedisScript<Long> roomRollbackReserveScript;
    private final JoinRoomProducer joinRoomProducer;
    private final RoomMapper roomMapper;

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

    private void ensureRoomMetaInitialized(Long roomId) {
        String metaKey = "room:" + roomId + ":meta";
        Boolean hasMax = stringRedisTemplate.opsForHash().hasKey(metaKey, "max");
        if (hasMax != null && hasMax) {
            return;
        }
        // 首次访问房间时，补全 Redis 中关于容量、状态等元信息，避免 Lua 脚本缺少关键字段
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            return;
        }
        stringRedisTemplate.opsForHash().put(metaKey, "max", String.valueOf(room.getMaxMembers() == null ? 0 : room.getMaxMembers()));
        stringRedisTemplate.opsForHash().put(metaKey, "confirmed", String.valueOf(room.getCurrentMembers() == null ? 0 : room.getCurrentMembers()));
        stringRedisTemplate.opsForHash().put(metaKey, "reserved", "0");
        stringRedisTemplate.opsForHash().put(metaKey, "status", String.valueOf(room.getStatus() == null ? 0 : room.getStatus()));
        long expireAtMs = room.getExpireTime() == null ? 0L : room.getExpireTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        stringRedisTemplate.opsForHash().put(metaKey, "expireAt", String.valueOf(expireAtMs));
        // 补全密码模式字段
        int passwordMode = room.getPasswordMode() == null ? 0 : room.getPasswordMode();
        stringRedisTemplate.opsForHash().put(metaKey, "passwordMode", String.valueOf(passwordMode));
        if (log.isDebugEnabled()) {
            log.debug("[房间服务][join-async] 房间={} 初始化缓存元数据 meta={}", roomId, stringRedisTemplate.opsForHash().entries(metaKey));
        }
    }

    @Override
    public PreReserveResult preReserve(Long roomId, Long userId, String displayName) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String metaKey = "room:" + roomId + ":meta";
        String membersKey = "room:" + roomId + ":members";
        String pendingKey = "room:" + roomId + ":pending";
        String tokenKey = "join:" + token;

        // 预占之前先确保 Redis 元数据齐全（尤其是 max/confirmed/reserved）
        ensureRoomMetaInitialized(roomId);

        if (log.isDebugEnabled()) {
            // 诊断：记录预占前各 redis key 快照，便于排查用尽或脏数据
            RoomRedisDiagnostics snapshot = snapshotRoomState(roomId);
            log.debug("[房间服务][join-async] 预占开始 房间={} 用户={} 显示名={} meta={} pendingKeys={} membersSize={}", roomId, userId, displayName,
                    snapshot.getMeta(), snapshot.getPending().keySet(), snapshot.getMembers().size());
        }

        // 执行 Lua 脚本完成原子校验与预占（reserved++、pending 写入 token）
        Long res = stringRedisTemplate.execute(
                roomPreReserveScript,
                Arrays.asList(metaKey, membersKey, pendingKey, tokenKey),
                String.valueOf(userId),
                displayName == null ? "" : displayName,
                token,
                String.valueOf(Instant.now().toEpochMilli()),
                String.valueOf(tokenTtlSeconds)
        );
        if (res == null) {
            return PreReserveResult.of(ReserveStatus.PROCESSING, null, "系统繁忙，请重试");
        }
        ReserveStatus status;
        switch (res.intValue()) {
            case 0: status = ReserveStatus.ACCEPTED; break;
            case 1: status = ReserveStatus.ALREADY_JOINED; break;
            case 2: status = ReserveStatus.FULL; break;
            case 3: status = ReserveStatus.CLOSED; break;
            case 4: status = ReserveStatus.EXPIRED; break;
            case 5: status = ReserveStatus.PROCESSING; break;
            default: status = ReserveStatus.PROCESSING;
        }
        if (log.isDebugEnabled()) {
            // 诊断：记录预占完成后 reserved/pending 的变化
            RoomRedisDiagnostics snapshot = snapshotRoomState(roomId);
            log.debug("[房间服务][join-async] 预占完成 房间={} 用户={} token={} 状态={} meta={} reserved={} pending={}", roomId, userId, token, status,
                    snapshot.getMeta(), snapshot.getMeta().getOrDefault("reserved", ""), snapshot.getPending());
        }
        if (status == ReserveStatus.ACCEPTED) {
            // 预占成功后发送 MQ 消息，由异步消费者处理正式入房
            JoinRoomRequestedEvent event = new JoinRoomRequestedEvent(roomId, userId, displayName, token, System.currentTimeMillis());
            boolean sent = joinRoomProducer.sendRequested(event);
            if (!sent) {
                // 即时回滚预占
                stringRedisTemplate.execute(
                        roomRollbackReserveScript,
                        Arrays.asList(metaKey, pendingKey),
                        String.valueOf(userId),
                        tokenKey
                );
                return PreReserveResult.of(ReserveStatus.PROCESSING, null, "系统繁忙，请重试");
            }
            return PreReserveResult.of(status, token, "已受理");
        }
        if (status == ReserveStatus.PROCESSING) {
            // 若 Lua 判断已有未完成的预占，记录现场帮助定位 pending 残留
            RoomRedisDiagnostics snapshot = snapshotRoomState(roomId);
            log.warn("[房间服务][join-async] 预占返回处理中 房间={} 用户={} token={} meta={} pending={} members={}",
                    roomId, userId, token, snapshot.getMeta(), snapshot.getPending(), snapshot.getMembers());
        }
        return PreReserveResult.of(status, null, status.name());
    }

    RoomRedisDiagnostics snapshotRoomState(Long roomId) {
        try {
            // 诊断工具：集中读取 meta、pending、members 三类 key，方便日志输出
            Map<String, String> meta = readHash(metaKey(roomId));
            Map<String, String> pending = readHash(pendingKey(roomId));
            Set<String> members = readSet(membersKey(roomId));
            return new RoomRedisDiagnostics(meta, pending, members);
        } catch (Exception e) {
            log.warn("[房间服务][join-async] 获取房间={} Redis快照失败", roomId, e);
            return new RoomRedisDiagnostics(Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet());
        }
    }

    private String metaKey(Long roomId) {
        return "room:" + roomId + ":meta";
    }

    private String pendingKey(Long roomId) {
        return "room:" + roomId + ":pending";
    }

    private String membersKey(Long roomId) {
        return "room:" + roomId + ":members";
    }

    private Map<String, String> readHash(String key) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        Map<String, String> result = new HashMap<>();
        entries.forEach((k, v) -> result.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
        return result;
    }

    private Set<String> readSet(String key) {
        Set<String> members = stringRedisTemplate.opsForSet().members(key);
        return members == null ? Collections.emptySet() : members;
    }

    @Getter
    @ToString
    static class RoomRedisDiagnostics {
        private final Map<String, String> meta;
        private final Map<String, String> pending;
        private final Set<String> members;

        RoomRedisDiagnostics(Map<String, String> meta, Map<String, String> pending, Set<String> members) {
            this.meta = meta;
            this.pending = pending;
            this.members = members;
        }
    }
} 