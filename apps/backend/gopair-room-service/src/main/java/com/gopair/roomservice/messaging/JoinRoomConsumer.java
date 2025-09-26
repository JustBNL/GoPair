package com.gopair.roomservice.messaging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gopair.roomservice.domain.event.JoinRoomRequestedEvent;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.domain.po.RoomMember;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JoinRoomConsumer {

    private final RoomMapper roomMapper;
    private final RoomMemberMapper roomMemberMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private String metaKey(Long roomId){ return "room:" + roomId + ":meta"; }
    private String membersKey(Long roomId){ return "room:" + roomId + ":members"; }
    private String pendingKey(Long roomId){ return "room:" + roomId + ":pending"; }

    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = "${mq.room-join.queue}")
    public void handle(JoinRoomRequestedEvent event) {
        Long roomId = event.getRoomId();
        Long userId = event.getUserId();
        String token = event.getJoinToken();
        try {
            boolean inserted = false;
            boolean incOk = false;

            // 幂等处理：若用户已存在则直接视为成功
            LambdaQueryWrapper<RoomMember> query = new LambdaQueryWrapper<>();
            query.eq(RoomMember::getRoomId, roomId).eq(RoomMember::getUserId, userId);
            RoomMember existing = roomMemberMapper.selectOne(query);
            if (existing == null) {
                // 插入成员（唯一键兜底）
                RoomMember member = new RoomMember();
                member.setRoomId(roomId);
                member.setUserId(userId);
                member.setDisplayName(event.getDisplayName());
                member.setRole(0);
                member.setStatus(0);
                member.setJoinTime(java.time.LocalDateTime.now());
                member.setLastActiveTime(java.time.LocalDateTime.now());
                try {
                    roomMemberMapper.insert(member);
                    inserted = true;
                } catch (Exception dup) {
                    inserted = false; // 唯一键冲突视为已存在
                }

                // 原子加一（仅在未满时）
                Room room = roomMapper.selectById(roomId);
                if (room != null && room.getStatus() != null && room.getStatus() == 0) {
                    int updated = roomMapper.incrementMembersIfNotFull(roomId);
                    if (updated == 1) {
                        incOk = true;
                        try { stringRedisTemplate.opsForHash().increment(metaKey(roomId), "confirmed", 1); } catch (Exception ignore) {}
                    } else {
                        log.warn("[房间服务][join-async] 房间{}成员计数未生效，token={}", roomId, token);
                    }
                }
            }

            // 统一释放 Redis 中的预占计数与 pending 标记
            stringRedisTemplate.opsForHash().increment(metaKey(roomId), "reserved", -1);
            stringRedisTemplate.opsForHash().delete(pendingKey(roomId), String.valueOf(userId));

            if (existing != null) {
                // 已存在：幂等成功
                stringRedisTemplate.opsForSet().add(membersKey(roomId), String.valueOf(userId));
                stringRedisTemplate.opsForValue().set("join:" + token, "JOINED");
                if (log.isDebugEnabled()) {
                    log.debug("[房间服务][join-async] 已存在成员 房间={} 用户={} token={} reserved={} pendingMembers={}",
                            roomId, userId, token,
                            stringRedisTemplate.opsForHash().get(metaKey(roomId), "reserved"),
                            stringRedisTemplate.opsForHash().keys(pendingKey(roomId)));
                }
                return;
            }

            if (inserted && incOk) {
                // 数据库插入成功且成员计数 +1，判定加入成功
                stringRedisTemplate.opsForSet().add(membersKey(roomId), String.valueOf(userId));
                stringRedisTemplate.opsForValue().set("join:" + token, "JOINED");
                log.info("[房间服务][join-async] 消费成功 房间={} 用户={} token={}", roomId, userId, token);
                if (log.isDebugEnabled()) {
                    log.debug("[房间服务][join-async] 消费成功后状态 房间={} metaReserved={} pendingMembers={} memberCount={}",
                            roomId,
                            stringRedisTemplate.opsForHash().get(metaKey(roomId), "reserved"),
                            stringRedisTemplate.opsForHash().keys(pendingKey(roomId)),
                            stringRedisTemplate.opsForSet().size(membersKey(roomId)));
                }
            } else {
                // 任一步失败：删除成员记录并标记 token 失败
                try {
                    LambdaQueryWrapper<RoomMember> del = new LambdaQueryWrapper<>();
                    del.eq(RoomMember::getRoomId, roomId).eq(RoomMember::getUserId, userId);
                    roomMemberMapper.delete(del);
                } catch (Exception ignore) {}
                stringRedisTemplate.opsForValue().set("join:" + token, "FAILED");
                log.warn("[房间服务][join-async] 消费补偿 房间={} 用户={} token={} inserted={} incOk={}", roomId, userId, token, inserted, incOk);
                if (log.isDebugEnabled()) {
                    log.debug("[房间服务][join-async] 补偿后状态 房间={} metaReserved={} pendingMembers={} memberCount={}",
                            roomId,
                            stringRedisTemplate.opsForHash().get(metaKey(roomId), "reserved"),
                            stringRedisTemplate.opsForHash().keys(pendingKey(roomId)),
                            stringRedisTemplate.opsForSet().size(membersKey(roomId)));
                }
            }
        } catch (Exception e) {
            log.error("[房间服务][join-async] 消费异常 房间={} 用户={} token={}", roomId, userId, token, e);
            try {
                stringRedisTemplate.opsForHash().increment(metaKey(roomId), "reserved", -1);
                stringRedisTemplate.opsForHash().delete(pendingKey(roomId), String.valueOf(userId));
                stringRedisTemplate.opsForValue().set("join:" + token, "FAILED");
                if (log.isDebugEnabled()) {
                    log.debug("[房间服务][join-async] 异常恢复 房间={} pendingMembers={} reserved={} memberCount={}",
                            roomId,
                            stringRedisTemplate.opsForHash().keys(pendingKey(roomId)),
                            stringRedisTemplate.opsForHash().get(metaKey(roomId), "reserved"),
                            stringRedisTemplate.opsForSet().size(membersKey(roomId)));
                }
            } catch (Exception ignore) {}
            throw e;
        }
    }
} 