package com.gopair.roomservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gopair.common.constants.MessageConstants;
import com.gopair.common.core.PageResult;
import com.gopair.common.entity.BaseQuery;

import com.gopair.common.util.BeanCopyUtils;
import com.gopair.roomservice.domain.dto.JoinRoomDto;
import com.gopair.roomservice.domain.dto.RoomDto;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.domain.po.RoomMember;
import com.gopair.roomservice.domain.vo.RoomMemberVO;
import com.gopair.roomservice.domain.vo.RoomVO;
import com.gopair.roomservice.enums.RoomErrorCode;
import com.gopair.roomservice.exception.RoomException;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import com.gopair.roomservice.service.RoomMemberService;
import com.gopair.roomservice.service.RoomService;
import com.gopair.roomservice.util.RoomCodeUtils;
import com.gopair.framework.logging.annotation.LogRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 房间服务实现类
 * 
 * @author gopair
 */
@Slf4j
@Service
public class RoomServiceImpl extends ServiceImpl<RoomMapper, Room> implements RoomService {

    private final RoomMapper roomMapper;
    private final RoomMemberMapper roomMemberMapper;
    private final RoomMemberService roomMemberService;

    public RoomServiceImpl(RoomMapper roomMapper, RoomMemberMapper roomMemberMapper, RoomMemberService roomMemberService) {
        this.roomMapper = roomMapper;
        this.roomMemberMapper = roomMemberMapper;
        this.roomMemberService = roomMemberService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "创建房间", module = "房间管理", includeResult = true)
    public RoomVO createRoom(RoomDto roomDto, Long userId) {
        // 参数验证
        if (!StringUtils.hasText(roomDto.getRoomName())) {
            throw new RoomException(RoomErrorCode.ROOM_NAME_EMPTY);
        }
        
        if (userId == null) {
            throw new RoomException(RoomErrorCode.USER_NOT_LOGGED_IN);
        }

        // 创建房间实体
        Room room = new Room();
        room.setRoomName(roomDto.getRoomName());
        room.setDescription(roomDto.getDescription());
        room.setMaxMembers(roomDto.getMaxMembers() != null ? roomDto.getMaxMembers() : 10);
        room.setCurrentMembers(1); // 创建者自动加入
        room.setOwnerId(userId);
        room.setStatus(0); // 活跃状态
        room.setVersion(0);
        
        // 设置过期时间
        int expireHours = roomDto.getExpireHours() != null ? roomDto.getExpireHours() : 24;
        room.setExpireTime(LocalDateTime.now().plusHours(expireHours));
        
        // 生成唯一房间码
        String roomCode = RoomCodeUtils.generateWithRetry(this::isRoomCodeUnique);
        room.setRoomCode(roomCode);

        // 保存房间
        if (roomMapper.insert(room) <= 0) {
            throw new RoomException(RoomErrorCode.ROOM_CREATION_FAILED);
        }

        // 创建者自动加入房间（房主角色）
        roomMemberService.addMember(room.getRoomId(), userId, "房主", 2);

        // 转换为VO返回
        RoomVO roomVO = BeanCopyUtils.copyBean(room, RoomVO.class);
        log.info("用户{}创建房间成功，房间ID：{}，房间码：{}", userId, room.getRoomId(), room.getRoomCode());
        
        return roomVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "加入房间", module = "房间管理", includeResult = true)
    public RoomVO joinRoom(JoinRoomDto joinRoomDto, Long userId) {
        // 参数验证
        if (!StringUtils.hasText(joinRoomDto.getRoomCode())) {
            throw new RoomException(RoomErrorCode.ROOM_CODE_INVALID);
        }
        
        if (userId == null) {
            throw new RoomException(RoomErrorCode.USER_NOT_LOGGED_IN);
        }
        
        if (!StringUtils.hasText(joinRoomDto.getDisplayName())) {
            throw new RoomException(RoomErrorCode.NICKNAME_EMPTY);
        }

        // 查找房间
        Room room = roomMapper.selectByRoomCode(joinRoomDto.getRoomCode());
        if (room == null) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND);
        }

        // 检查房间状态
        if (room.getStatus() != 0) {
            throw new RoomException(RoomErrorCode.ROOM_CLOSED);
        }

        // 检查房间是否过期
        if (room.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new RoomException(RoomErrorCode.ROOM_EXPIRED);
        }

        // 检查是否已在房间中
        if (roomMemberService.isMemberInRoom(room.getRoomId(), userId)) {
            throw new RoomException(RoomErrorCode.ALREADY_IN_ROOM);
        }

        // 检查房间是否已满
        if (room.getCurrentMembers() >= room.getMaxMembers()) {
            throw new RoomException(RoomErrorCode.ROOM_FULL);
        }

        // 添加成员（普通成员角色）
        roomMemberService.addMember(room.getRoomId(), userId, joinRoomDto.getDisplayName(), 0);

        // 更新房间成员数（使用乐观锁）
        int updateRows = roomMapper.updateCurrentMembers(room.getRoomId(), 
            room.getCurrentMembers() + 1, room.getVersion());
        if (updateRows == 0) {
            throw new RoomException(RoomErrorCode.ROOM_STATE_CHANGED);
        }

        // 重新查询房间信息
        room = roomMapper.selectById(room.getRoomId());
        RoomVO roomVO = BeanCopyUtils.copyBean(room, RoomVO.class);
        
        log.info("用户{}加入房间成功，房间ID：{}，房间码：{}", userId, room.getRoomId(), room.getRoomCode());
        
        return roomVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean leaveRoom(Long roomId, Long userId) {
        if (userId == null) {
            throw new RoomException(RoomErrorCode.USER_NOT_LOGGED_IN);
        }
        
        // 检查是否在房间中
        if (!roomMemberService.isMemberInRoom(roomId, userId)) {
            throw new RoomException(RoomErrorCode.NOT_IN_ROOM);
        }

        // 移除成员
        boolean removed = roomMemberService.removeMember(roomId, userId);
        if (!removed) {
            return false;
        }

        // 更新房间成员数
        Room room = roomMapper.selectById(roomId);
        if (room != null && room.getCurrentMembers() > 0) {
            roomMapper.updateCurrentMembers(roomId, room.getCurrentMembers() - 1, room.getVersion());
            
            // 如果房间没有成员了，关闭房间
            if (room.getCurrentMembers() - 1 == 0) {
                room.setStatus(1);
                room.setUpdateTime(LocalDateTime.now());
                roomMapper.updateById(room);
                log.info("房间{}因无成员自动关闭", roomId);
            }
        }

        log.info("用户{}离开房间{}成功", userId, roomId);
        return true;
    }

    @Override
    public RoomVO getRoomByCode(String roomCode) {
        if (!RoomCodeUtils.isValidFormat(roomCode)) {
            throw new RoomException(RoomErrorCode.ROOM_CODE_INVALID);
        }
        
        Room room = roomMapper.selectByRoomCode(roomCode);
        if (room == null) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND);
        }
        
        return BeanCopyUtils.copyBean(room, RoomVO.class);
    }

    @Override
    public PageResult<RoomVO> getUserRooms(Long userId, BaseQuery query) {
        if (userId == null) {
            throw new RoomException(RoomErrorCode.USER_NOT_LOGGED_IN);
        }

        // 调用RoomMemberService的正确实现获取用户的所有相关房间
        PageResult<RoomVO> memberRooms = roomMemberService.getUserRooms(userId, query);
        
        // 为房间列表增强用户关系信息
        enhanceRoomsWithUserRelationship(memberRooms.getRecords(), userId);
        
        log.info("用户{}获取房间列表成功，共{}个房间", userId, memberRooms.getTotal());

        return memberRooms;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeRoom(Long roomId, Long userId) {
        if (userId == null) {
            throw new RoomException(RoomErrorCode.USER_NOT_LOGGED_IN);
        }
        
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND);
        }

        // 检查权限（只有房主可以关闭房间）
        if (!room.getOwnerId().equals(userId)) {
            throw new RoomException(RoomErrorCode.USER_NOT_LOGGED_IN);
        }

        // 关闭房间
        room.setStatus(1);
        room.setUpdateTime(LocalDateTime.now());
        int updateRows = roomMapper.updateById(room);

        if (updateRows > 0) {
            log.info("房间{}已被房主{}关闭", roomId, userId);
        }

        return updateRows > 0;
    }

    @Override
    public List<RoomMemberVO> getRoomMembers(Long roomId) {
        if (roomId == null) {
            throw new RoomException(RoomErrorCode.USER_NOT_LOGGED_IN);
        }
        
        return roomMemberService.getRoomMembers(roomId);
    }

    @Override
    public List<Room> findExpiredRooms() {
        return roomMapper.selectExpiredRooms(LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRoomCompletely(Long roomId) {
        try {
            // 删除房间成员
            roomMemberService.deleteByRoomId(roomId);
            
            // 删除房间
            int deleteRows = roomMapper.deleteById(roomId);
            
            log.info("房间{}已完全删除", roomId);
            return deleteRows > 0;
        } catch (Exception e) {
            log.error("删除房间{}失败", roomId, e);
            return false;
        }
    }

    @Override
    public boolean isRoomCodeUnique(String roomCode) {
        Room room = roomMapper.selectByRoomCode(roomCode);
        return room == null;
    }

    /**
     * 为房间列表增强用户关系信息
     * 
     * @param rooms 房间列表
     * @param userId 用户ID
     */
    private void enhanceRoomsWithUserRelationship(List<RoomVO> rooms, Long userId) {
        if (rooms == null || rooms.isEmpty() || userId == null) {
            return;
        }

        for (RoomVO room : rooms) {
            try {
                // 获取用户在房间中的成员信息
                RoomMember membership = getUserRoomMembership(room.getRoomId(), userId);
                
                if (membership != null) {
                    // 设置用户角色
                    room.setUserRole(membership.getRole());
                    room.setJoinTime(membership.getJoinTime());
                    
                    // 根据角色和房主信息确定关系类型
                    if (room.getOwnerId().equals(userId) || (membership.getRole() != null && membership.getRole() == 2)) {
                        room.setRelationshipType("created");
                    } else {
                        room.setRelationshipType("joined");
                    }
                } else {
                    // 降级处理：通过房主ID判断
                    if (room.getOwnerId().equals(userId)) {
                        room.setUserRole(2); // 房主角色
                        room.setRelationshipType("created");
                        room.setJoinTime(room.getCreateTime());
                    } else {
                        room.setUserRole(0); // 普通成员
                        room.setRelationshipType("joined");
                        log.warn("用户{}在房间{}中的成员信息缺失，使用降级处理", userId, room.getRoomId());
                    }
                }
            } catch (Exception e) {
                log.error("增强房间{}的用户关系信息失败", room.getRoomId(), e);
                // 设置默认值，不影响主流程
                room.setUserRole(0);
                room.setRelationshipType("joined");
            }
        }
        
        log.info("为用户{}增强了{}个房间的关系信息", userId, rooms.size());
    }

    /**
     * 获取用户在房间中的成员信息
     * 
     * @param roomId 房间ID
     * @param userId 用户ID
     * @return 房间成员信息，如果不存在返回null
     */
    private RoomMember getUserRoomMembership(Long roomId, Long userId) {
        if (roomId == null || userId == null) {
            return null;
        }

        try {
            LambdaQueryWrapper<RoomMember> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(RoomMember::getRoomId, roomId)
                       .eq(RoomMember::getUserId, userId);
            
            return roomMemberMapper.selectOne(queryWrapper);
        } catch (Exception e) {
            log.error("查询用户{}在房间{}中的成员信息失败", userId, roomId, e);
            return null;
        }
    }
} 