package com.gopair.roomservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gopair.common.core.PageResult;
import com.gopair.common.entity.BaseQuery;
import com.gopair.common.util.BeanCopyUtils;
import com.gopair.roomservice.domain.dto.JoinRoomDto;
import com.gopair.roomservice.domain.dto.RoomDto;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.domain.vo.RoomMemberVO;
import com.gopair.roomservice.domain.vo.RoomVO;
import com.gopair.roomservice.enums.RoomErrorCode;
import com.gopair.roomservice.exception.RoomException;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.service.RoomMemberService;
import com.gopair.roomservice.service.RoomService;
import com.gopair.roomservice.util.RoomCodeUtils;
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
    private final RoomMemberService roomMemberService;

    public RoomServiceImpl(RoomMapper roomMapper, RoomMemberService roomMemberService) {
        this.roomMapper = roomMapper;
        this.roomMemberService = roomMemberService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoomVO createRoom(RoomDto roomDto, Long userId) {
        // 参数验证
        if (!StringUtils.hasText(roomDto.getRoomName())) {
            throw new RoomException(RoomErrorCode.ROOM_NAME_EMPTY);
        }
        
        if (userId == null) {
            throw new RoomException(RoomErrorCode.NO_PERMISSION, "用户未登录");
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

        // 设置审计字段
        room.setCreateBy("用户" + userId);
        room.setCreateTime(LocalDateTime.now());

        // 保存房间
        if (roomMapper.insert(room) <= 0) {
            throw new RoomException(RoomErrorCode.ROOM_CODE_GENERATION_FAILED, "房间创建失败");
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
    public RoomVO joinRoom(JoinRoomDto joinRoomDto, Long userId) {
        // 参数验证
        if (!StringUtils.hasText(joinRoomDto.getRoomCode())) {
            throw new RoomException(RoomErrorCode.ROOM_CODE_INVALID);
        }
        
        if (userId == null) {
            throw new RoomException(RoomErrorCode.NO_PERMISSION, "用户未登录");
        }
        
        if (!StringUtils.hasText(joinRoomDto.getDisplayName())) {
            throw new RoomException(RoomErrorCode.NICKNAME_EMPTY, "显示名称不能为空");
        }

        // 查找房间
        Room room = roomMapper.selectByRoomCode(joinRoomDto.getRoomCode());
        if (room == null) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND);
        }

        // 检查房间状态
        if (room.getStatus() != 0) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND, "房间已关闭");
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
            throw new RoomException(RoomErrorCode.ROOM_FULL, "房间状态已变更，请重试");
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
            throw new RoomException(RoomErrorCode.NO_PERMISSION, "用户未登录");
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
            throw new RoomException(RoomErrorCode.NO_PERMISSION, "用户未登录");
        }

        // 查询用户创建的房间
        Page<Room> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<Room> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Room::getOwnerId, userId)
                   .eq(Room::getStatus, 0)
                   .orderByDesc(Room::getCreateTime);

        IPage<Room> roomPage = roomMapper.selectPage(page, queryWrapper);
        List<RoomVO> roomVOList = BeanCopyUtils.copyBeanList(roomPage.getRecords(), RoomVO.class);

        return new PageResult<RoomVO>(roomVOList, roomPage.getTotal(), roomPage.getCurrent(), roomPage.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeRoom(Long roomId, Long userId) {
        if (userId == null) {
            throw new RoomException(RoomErrorCode.NO_PERMISSION, "用户未登录");
        }
        
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND);
        }

        // 检查权限（只有房主可以关闭房间）
        if (!userId.equals(room.getOwnerId())) {
            throw new RoomException(RoomErrorCode.NO_PERMISSION);
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
} 