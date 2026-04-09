package com.gopair.roomservice.messaging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.domain.event.LeaveRoomRequestedEvent;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.domain.po.RoomMember;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import com.gopair.framework.logging.annotation.LogRecord;
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

    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = "${mq.room-leave.queue}")
    @LogRecord(operation = "消费离开房间事件", module = "消息消费")
    public void handle(LeaveRoomRequestedEvent event) {
        Long roomId = event.getRoomId();
        Long userId = event.getUserId();
        try {
            // 幂等删除成员
            LambdaQueryWrapper<RoomMember> query = new LambdaQueryWrapper<>();
            query.eq(RoomMember::getRoomId, roomId).eq(RoomMember::getUserId, userId);
            RoomMember existing = roomMemberMapper.selectOne(query);
            boolean removed = false;
            if (existing != null) {
                removed = roomMemberMapper.delete(query) > 0;
            }

            // 仅在真实删除成员后递减人数，避免重复消费导致人数错误递减
            if (removed) {
                int dec = roomMapper.decrementMembersIfPositive(roomId);
                if (dec == 1) {
                    try { stringRedisTemplate.opsForHash().increment(RoomConst.metaKey(roomId), RoomConst.FIELD_CONFIRMED, -1); } catch (Exception ignore) {}
                }
            }

            // Redis：成员集合移除
            try { stringRedisTemplate.opsForSet().remove(RoomConst.membersKey(roomId), String.valueOf(userId)); } catch (Exception ignore) {}

            // 若房间无人，尝试关闭
            Room room = roomMapper.selectById(roomId);
            if (room != null && room.getCurrentMembers() != null && room.getCurrentMembers() == 0
                    && (room.getStatus() == null || room.getStatus() == RoomConst.STATUS_ACTIVE)) {
                room.setStatus(RoomConst.STATUS_CLOSED);
                roomMapper.updateById(room);
                try { stringRedisTemplate.opsForHash().put(RoomConst.metaKey(roomId), RoomConst.FIELD_STATUS,
                        String.valueOf(RoomConst.STATUS_CLOSED)); } catch (Exception ignore) {}
                log.info("[房间服务][leave] 房间{}因无成员自动关闭", roomId);
            }
        } catch (Exception e) {
            log.error("[房间服务][leave] 消费异常 房间={} 用户={}", roomId, userId, e);
            throw e;
        }
    }
}
