package com.gopair.roomservice.messaging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gopair.roomservice.domain.event.LeaveRoomRequestedEvent;
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
public class LeaveRoomConsumer {

    private final RoomMapper roomMapper;
    private final RoomMemberMapper roomMemberMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private String metaKey(Long roomId){ return "room:" + roomId + ":meta"; }
    private String membersKey(Long roomId){ return "room:" + roomId + ":members"; }

    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = "${mq.room-leave.queue}")
    public void handle(LeaveRoomRequestedEvent event) {
        Long roomId = event.getRoomId();
        Long userId = event.getUserId();
        try {
            // 幂等删除成员
            LambdaQueryWrapper<RoomMember> query = new LambdaQueryWrapper<>();
            query.eq(RoomMember::getRoomId, roomId).eq(RoomMember::getUserId, userId);
            RoomMember existing = roomMemberMapper.selectOne(query);
            if (existing != null) {
                roomMemberMapper.delete(query);
            }

            // 原子减一（仅当 >0）
            int dec = roomMapper.decrementMembersIfPositive(roomId);
            if (dec == 1) {
                try { stringRedisTemplate.opsForHash().increment(metaKey(roomId), "confirmed", -1); } catch (Exception ignore) {}
            }

            // Redis：成员集合移除
            try { stringRedisTemplate.opsForSet().remove(membersKey(roomId), String.valueOf(userId)); } catch (Exception ignore) {}

            // 若房间无人，尝试关闭
            Room room = roomMapper.selectById(roomId);
            if (room != null && room.getCurrentMembers() != null && room.getCurrentMembers() == 0 && (room.getStatus() == null || room.getStatus() == 0)) {
                room.setStatus(1);
                roomMapper.updateById(room);
                try { stringRedisTemplate.opsForHash().put(metaKey(roomId), "status", "1"); } catch (Exception ignore) {}
                log.info("[房间服务][leave] 房间{}因无成员自动关闭", roomId);
            }
        } catch (Exception e) {
            log.error("[房间服务][leave] 消费异常 房间={} 用户={}", roomId, userId, e);
            throw e;
        }
    }
} 