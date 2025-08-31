package com.gopair.roomservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gopair.common.core.PageResult;
import com.gopair.common.entity.BaseQuery;
import com.gopair.common.util.BeanCopyUtils;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.domain.po.RoomMember;
import com.gopair.roomservice.domain.vo.RoomMemberVO;
import com.gopair.roomservice.domain.vo.RoomVO;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import com.gopair.roomservice.service.RoomMemberService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 房间成员服务实现类
 * 
 * @author gopair
 */
@Slf4j
@Service
public class RoomMemberServiceImpl extends ServiceImpl<RoomMemberMapper, RoomMember> implements RoomMemberService {

    private final RoomMemberMapper roomMemberMapper;
    private final RoomMapper roomMapper;

    public RoomMemberServiceImpl(RoomMemberMapper roomMemberMapper, RoomMapper roomMapper) {
        this.roomMemberMapper = roomMemberMapper;
        this.roomMapper = roomMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addMember(Long roomId, Long userId, String displayName, Integer role) {
        // 检查是否已经在房间中
        if (isMemberInRoom(roomId, userId)) {
            return false;
        }

        // 创建房间成员记录
        RoomMember roomMember = new RoomMember();
        roomMember.setRoomId(roomId);
        roomMember.setUserId(userId);
        roomMember.setDisplayName(displayName);
        roomMember.setRole(role != null ? role : 0);
        roomMember.setStatus(0); // 在线状态
        roomMember.setJoinTime(LocalDateTime.now());
        roomMember.setLastActiveTime(LocalDateTime.now());

        int insertRows = roomMemberMapper.insert(roomMember);
        
        if (insertRows > 0) {
            log.info("用户{}加入房间{}成功，角色：{}", userId, roomId, role);
        }
        
        return insertRows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeMember(Long roomId, Long userId) {
        LambdaQueryWrapper<RoomMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RoomMember::getRoomId, roomId)
                   .eq(RoomMember::getUserId, userId);

        int deleteRows = roomMemberMapper.delete(queryWrapper);
        
        if (deleteRows > 0) {
            log.info("用户{}离开房间{}成功", userId, roomId);
        }
        
        return deleteRows > 0;
    }

    @Override
    public boolean isMemberInRoom(Long roomId, Long userId) {
        LambdaQueryWrapper<RoomMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RoomMember::getRoomId, roomId)
                   .eq(RoomMember::getUserId, userId);
        
        RoomMember member = roomMemberMapper.selectOne(queryWrapper);
        return member != null;
    }

    @Override
    public List<RoomMemberVO> getRoomMembers(Long roomId) {
        LambdaQueryWrapper<RoomMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RoomMember::getRoomId, roomId)
                   .orderByDesc(RoomMember::getJoinTime);
        
        List<RoomMember> members = roomMemberMapper.selectList(queryWrapper);
        
        return members.stream().map(member -> {
            RoomMemberVO memberVO = BeanCopyUtils.copyBean(member, RoomMemberVO.class);
            return memberVO;
        }).collect(Collectors.toList());
    }

    @Override
    public PageResult<RoomVO> getUserRooms(Long userId, BaseQuery query) {
        // 通过房间成员表查询用户加入的房间
        Page<RoomMember> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<RoomMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RoomMember::getUserId, userId)
                   .orderByDesc(RoomMember::getJoinTime);

        IPage<RoomMember> memberPage = roomMemberMapper.selectPage(page, queryWrapper);
        
        // 获取房间信息
        List<Long> roomIds = memberPage.getRecords().stream()
                .map(RoomMember::getRoomId)
                .collect(Collectors.toList());
        
        if (roomIds.isEmpty()) {
            return new PageResult<>(List.of(), 0L, page.getCurrent(), page.getSize());
        }
        
        LambdaQueryWrapper<Room> roomQueryWrapper = new LambdaQueryWrapper<>();
        roomQueryWrapper.in(Room::getRoomId, roomIds)
                       .eq(Room::getStatus, 0);
        
        List<Room> rooms = roomMapper.selectList(roomQueryWrapper);
        List<RoomVO> roomVOList = BeanCopyUtils.copyBeanList(rooms, RoomVO.class);

        return new PageResult<>(roomVOList, memberPage.getTotal(), memberPage.getCurrent(), memberPage.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByRoomId(Long roomId) {
        LambdaQueryWrapper<RoomMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RoomMember::getRoomId, roomId);
        
        int deleteRows = roomMemberMapper.delete(queryWrapper);
        
        if (deleteRows > 0) {
            log.info("房间{}的所有成员已删除", roomId);
        }
        
        return deleteRows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateLastActiveTime(Long roomId, Long userId) {
        LambdaQueryWrapper<RoomMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RoomMember::getRoomId, roomId)
                   .eq(RoomMember::getUserId, userId);
        
        RoomMember member = new RoomMember();
        member.setLastActiveTime(LocalDateTime.now());
        
        int updateRows = roomMemberMapper.update(member, queryWrapper);
        
        return updateRows > 0;
    }
} 