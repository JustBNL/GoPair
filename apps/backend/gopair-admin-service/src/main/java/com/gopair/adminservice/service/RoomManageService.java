package com.gopair.adminservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.adminservice.domain.po.Room;
import com.gopair.adminservice.domain.po.RoomMember;
import com.gopair.adminservice.domain.po.User;
import com.gopair.adminservice.enums.AdminErrorCode;
import com.gopair.adminservice.exception.AdminException;
import com.gopair.adminservice.mapper.RoomMapper;
import com.gopair.adminservice.mapper.RoomMemberMapper;
import com.gopair.adminservice.mapper.UserMapper;
import com.gopair.adminservice.messaging.RoomStatusChangeProducer;
import com.gopair.adminservice.context.AdminContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 房间管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomManageService {

    private final RoomMapper roomMapper;
    private final RoomMemberMapper roomMemberMapper;
    private final UserMapper userMapper;
    private final RoomStatusChangeProducer roomStatusChangeProducer;

    public record RoomPageQuery(Integer pageNum, Integer pageSize, Integer status, String keyword) {}

    public Page<Room> getRoomPage(RoomPageQuery query) {
        Page<Room> page = new Page<>(query.pageNum(), query.pageSize());
        LambdaQueryWrapper<Room> wrapper = new LambdaQueryWrapper<>();
        if (query.status() != null) {
            wrapper.eq(Room::getStatus, query.status());
        }
        if (StringUtils.hasText(query.keyword())) {
            wrapper.like(Room::getRoomName, query.keyword())
                   .or()
                   .like(Room::getRoomCode, query.keyword());
        }
        wrapper.orderByDesc(Room::getCreateTime);
        return roomMapper.selectPage(page, wrapper);
    }

    public Room getRoomById(Long roomId) {
        return roomMapper.selectById(roomId);
    }

    public Map<String, Object> getRoomDetail(Long roomId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new AdminException(AdminErrorCode.ROOM_NOT_FOUND);
        }
        LambdaQueryWrapper<RoomMember> memberQ = new LambdaQueryWrapper<RoomMember>()
                .eq(RoomMember::getRoomId, roomId);
        // 活跃房间只展示当前成员，已关闭房间展示全部成员（含历史离开的）
        if (room.getStatus() == null || room.getStatus() != 1) {
            memberQ.isNull(RoomMember::getLeaveTime);
        }
        memberQ.orderByDesc(RoomMember::getJoinTime);
        List<RoomMember> members = roomMemberMapper.selectList(memberQ);
        Map<Long, User> userMap = new HashMap<>();
        if (!members.isEmpty()) {
            List<Long> userIds = members.stream().map(RoomMember::getUserId).collect(Collectors.toList());
            userMapper.selectBatchIds(userIds).forEach(u -> userMap.put(u.getUserId(), u));
        }
        Map<String, Object> detail = new HashMap<>();
        detail.put("room", room);
        detail.put("members", members);
        detail.put("userMap", userMap);
        return detail;
    }

    public void closeRoom(Long roomId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new AdminException(AdminErrorCode.ROOM_NOT_FOUND);
        }
        room.setStatus(1);
        room.setClosedTime(LocalDateTime.now());
        roomMapper.updateById(room);
        log.info("[RoomManage] 强制关闭房间: roomId={}", roomId);
    }

    public void disableRoom(Long roomId, String reason) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new AdminException(AdminErrorCode.ROOM_NOT_FOUND);
        }
        room.setStatus(4);
        room.setDisabledTime(LocalDateTime.now());
        room.setDisabledReason(reason);
        roomMapper.updateById(room);
        log.info("[RoomManage] 禁用房间: roomId={} reason={}", roomId, reason);

        Long adminId = getCurrentAdminId();
        roomStatusChangeProducer.sendDisable(roomId, adminId, reason);
    }

    public void enableRoom(Long roomId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new AdminException(AdminErrorCode.ROOM_NOT_FOUND);
        }
        room.setStatus(0);
        room.setDisabledTime(null);
        room.setDisabledReason(null);
        roomMapper.updateById(room);
        log.info("[RoomManage] 解禁房间: roomId={}", roomId);

        Long adminId = getCurrentAdminId();
        roomStatusChangeProducer.sendEnable(roomId, adminId);
    }

    private Long getCurrentAdminId() {
        var ctx = AdminContextHolder.get();
        return ctx != null ? ctx.getAdminId() : null;
    }
}
