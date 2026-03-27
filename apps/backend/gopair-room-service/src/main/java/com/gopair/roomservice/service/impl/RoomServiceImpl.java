package com.gopair.roomservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gopair.common.core.PageResult;
import com.gopair.common.entity.BaseQuery;

import com.gopair.common.util.BeanCopyUtils;
import com.gopair.roomservice.domain.dto.JoinRoomDto;
import com.gopair.roomservice.domain.dto.RoomDto;
import com.gopair.roomservice.domain.event.LeaveRoomRequestedEvent;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.domain.po.RoomMember;
import com.gopair.roomservice.domain.vo.JoinAcceptedVO;
import com.gopair.roomservice.domain.vo.RoomMemberVO;
import com.gopair.roomservice.domain.vo.RoomVO;
import com.gopair.roomservice.enums.RoomErrorCode;
import com.gopair.roomservice.exception.RoomException;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import com.gopair.roomservice.messaging.LeaveRoomProducer;
import com.gopair.roomservice.service.JoinReservationService;
import com.gopair.roomservice.service.JoinResultQueryService;
import com.gopair.roomservice.service.RoomCacheSyncService;
import com.gopair.roomservice.service.RoomMemberService;
import com.gopair.roomservice.service.RoomService;
import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.roomservice.util.RoomCodeUtils;
import com.gopair.roomservice.util.PasswordUtils;
import com.gopair.roomservice.config.RoomConfig;
import com.gopair.framework.context.UserContextHolder;
import com.gopair.framework.logging.annotation.LogRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;


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
    private final JoinReservationService joinReservationService;
    private final JoinResultQueryService joinResultQueryService;
    private final RoomCacheSyncService roomCacheSyncService;
    private final LeaveRoomProducer leaveRoomProducer;
    private final StringRedisTemplate stringRedisTemplate;
    private final WebSocketMessageProducer wsProducer;
    private final RoomConfig roomConfig;

    public RoomServiceImpl(RoomMapper roomMapper, RoomMemberMapper roomMemberMapper, RoomMemberService roomMemberService,
                           JoinReservationService joinReservationService, JoinResultQueryService joinResultQueryService,
                           RoomCacheSyncService roomCacheSyncService, LeaveRoomProducer leaveRoomProducer,
                           StringRedisTemplate stringRedisTemplate, WebSocketMessageProducer wsProducer,
                           RoomConfig roomConfig) {
        this.roomMapper = roomMapper;
        this.roomMemberMapper = roomMemberMapper;
        this.roomMemberService = roomMemberService;
        this.joinReservationService = joinReservationService;
        this.joinResultQueryService = joinResultQueryService;
        this.roomCacheSyncService = roomCacheSyncService;
        this.leaveRoomProducer = leaveRoomProducer;
        this.stringRedisTemplate = stringRedisTemplate;
        this.wsProducer = wsProducer;
        this.roomConfig = roomConfig;
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

        // 预设密码模式与可见性（insert 前先写入，insert 后再补充 passwordHash）
        int passwordMode = roomDto.getPasswordMode() != null ? roomDto.getPasswordMode() : 0;
        room.setPasswordMode(passwordMode);
        room.setPasswordVisible(roomDto.getPasswordVisible() != null ? roomDto.getPasswordVisible() : 1);
        if (passwordMode == 1 && !StringUtils.hasText(roomDto.getRawPassword())) {
            throw new RoomException(RoomErrorCode.PASSWORD_REQUIRED);
        }

        // 保存房间（insert 后 MyBatis-Plus 自动回填 roomId）
        if (roomMapper.insert(room) <= 0) {
            throw new RoomException(RoomErrorCode.ROOM_CREATION_FAILED);
        }

        // insert 后使用真实 roomId 计算密码 Hash 并更新
        String masterKey = roomConfig.getPassword().getMasterKey();
        if (passwordMode == 1) {
            room.setPasswordHash(PasswordUtils.encryptPassword(roomDto.getRawPassword(), room.getRoomId(), masterKey));
            roomMapper.updateById(room);
        } else if (passwordMode == 2) {
            room.setPasswordHash(PasswordUtils.generateTotpSecret());
            roomMapper.updateById(room);
        }

        // 创建者自动加入房间（房主角色）
        roomMemberService.addMember(room.getRoomId(), userId, "房主", 2);

        // 转换为VO返回
        RoomVO roomVO = BeanCopyUtils.copyBean(room, RoomVO.class);
        log.info("[房间服务] 用户{}创建房间成功，房间ID：{}，房间码：{}", userId, room.getRoomId(), room.getRoomCode());
        
        // 事务提交后初始化 Redis（含房主与 confirmed=1）
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try { roomCacheSyncService.initializeRoomInCache(room, userId); } catch (Exception ignore) {}

                // 通知语音服务自动创建通话
                try {
                    wsProducer.sendEventToRoom(room.getRoomId(), "room_created", Map.of(
                            "roomId", room.getRoomId(),
                            "ownerId", userId
                    ));
                    log.info("[房间] 已发送 room_created 事件: roomId={}", room.getRoomId());
                } catch (Exception e) {
                    log.warn("[房间] 发送 room_created 事件失败，不影响房间创建: roomId={}, error={}",
                            room.getRoomId(), e.getMessage());
                }
            }
        });
        
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

        // 验证房间密码
        verifyRoomPassword(room, joinRoomDto.getPassword());

        // 检查是否已在房间中
        if (roomMemberService.isMemberInRoom(room.getRoomId(), userId)) {
            throw new RoomException(RoomErrorCode.ALREADY_IN_ROOM);
        }

        // 检查房间是否已满
        if (room.getCurrentMembers() >= room.getMaxMembers()) {
            throw new RoomException(RoomErrorCode.ROOM_FULL);
        }

        // 使用账号昵称作为房间内显示名称
        String displayName = UserContextHolder.getCurrentNickname();
        if (!StringUtils.hasText(displayName)) {
            displayName = "用户" + userId;
        }

        // 添加成员（普通成员角色）
        roomMemberService.addMember(room.getRoomId(), userId, displayName, 0);

        // 更新房间成员数（使用乐观锁）
        int updateRows = roomMapper.updateCurrentMembers(room.getRoomId(), 
            room.getCurrentMembers() + 1, room.getVersion());
        if (updateRows == 0) {
            throw new RoomException(RoomErrorCode.ROOM_STATE_CHANGED);
        }

        // 重新查询房间信息
        room = roomMapper.selectById(room.getRoomId());
        RoomVO roomVO = BeanCopyUtils.copyBean(room, RoomVO.class);
        
        log.info("[房间服务] 用户{}加入房间成功，房间ID：{}，房间码：{}", userId, room.getRoomId(), room.getRoomCode());
        
        return roomVO;
    }

    @Override
    public JoinAcceptedVO joinRoomAsync(JoinRoomDto joinRoomDto, Long userId) {
        if (!StringUtils.hasText(joinRoomDto.getRoomCode())) {
            throw new RoomException(RoomErrorCode.ROOM_CODE_INVALID);
        }
        if (userId == null) {
            throw new RoomException(RoomErrorCode.USER_NOT_LOGGED_IN);
        }
        Room room = roomMapper.selectByRoomCode(joinRoomDto.getRoomCode());
        if (room == null) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND);
        }

        // 验证房间密码（异步路径同样需要校验）
        verifyRoomPassword(room, joinRoomDto.getPassword());

        if (log.isDebugEnabled()) {
            // 记录异步入口，便于排查是否真正触发预占
            log.debug("[房间服务][join-async] 异步加入入口 房间码={} 房间ID={} 用户={}", joinRoomDto.getRoomCode(), room.getRoomId(), userId);
        }
        String nickname = UserContextHolder.getCurrentNickname();
        if (!StringUtils.hasText(nickname)) {
            nickname = "用户" + userId;
        }
        JoinReservationService.PreReserveResult result = joinReservationService.preReserve(room.getRoomId(), userId, nickname);
        if (log.isDebugEnabled()) {
            if (joinReservationService instanceof JoinReservationServiceImpl reservationService) {
                // 预占后再次记录 Redis 快照，监控 reserved/pending 变化
                JoinReservationServiceImpl.RoomRedisDiagnostics snapshot = reservationService.snapshotRoomState(room.getRoomId());
                log.debug("[房间服务][join-async] 预占结果 房间={} 用户={} 状态={} meta={} pending={} members={} ",
                        room.getRoomId(), userId, result.status, snapshot.getMeta(), snapshot.getPending(), snapshot.getMembers());
            }
        }
        switch (result.status) {
            case ACCEPTED:
                return new JoinAcceptedVO(result.joinToken, "已受理");
            case ALREADY_JOINED:
                return new JoinAcceptedVO(null, "已在房间");
            case FULL:
                throw new RoomException(RoomErrorCode.ROOM_FULL);
            case CLOSED:
                throw new RoomException(RoomErrorCode.ROOM_CLOSED);
            case EXPIRED:
                throw new RoomException(RoomErrorCode.ROOM_EXPIRED);
            case PROCESSING:
            default:
                if (joinReservationService instanceof JoinReservationServiceImpl reservationService) {
                    // 若状态为 PROCESSING 再次抓取 Redis 现场，以便识别挂起问题
                    JoinReservationServiceImpl.RoomRedisDiagnostics snapshot = reservationService.snapshotRoomState(room.getRoomId());
                    log.warn("[房间服务][join-async] 队列处理中 房间={} 用户={} meta={} pending={} members={} redisReserved={}",
                            room.getRoomId(), userId, snapshot.getMeta(), snapshot.getPending(), snapshot.getMembers(),
                            snapshot.getMeta().getOrDefault("reserved", ""));
                }
                throw new RoomException(RoomErrorCode.ROOM_STATE_CHANGED);
        }
    }

    @Override
    public JoinResultQueryService.JoinStatusVO queryJoinResult(String token) {
        return joinResultQueryService.queryByToken(token);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean leaveRoom(Long roomId, Long userId) {
        if (userId == null) {
            throw new RoomException(RoomErrorCode.USER_NOT_LOGGED_IN);
        }
        if (!roomMemberService.isMemberInRoom(roomId, userId)) {
            throw new RoomException(RoomErrorCode.NOT_IN_ROOM);
        }
        // 事件化：事务提交后投递 leave 事件
        String correlationId = UUID.randomUUID().toString();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    LeaveRoomRequestedEvent evt = new LeaveRoomRequestedEvent(roomId, userId, correlationId, System.currentTimeMillis());
                    boolean sent = leaveRoomProducer.sendRequested(evt);
                    if (!sent) {
                        // 降级：同步处理一次，保证用户体验
                try {
                    // 发布离开事件失败时采取降级处理，确保数据最终一致
                            LambdaQueryWrapper<RoomMember> q = new LambdaQueryWrapper<>();
                            q.eq(RoomMember::getRoomId, roomId).eq(RoomMember::getUserId, userId);
                            roomMemberMapper.delete(q);
                            int dec = roomMapper.decrementMembersIfPositive(roomId);
                            if (dec == 1) {
                                roomCacheSyncService.incrementConfirmed(roomId, -1);
                            }
                            roomCacheSyncService.removeMemberFromCache(roomId, userId);
                            Room room = roomMapper.selectById(roomId);
                            if (room != null && room.getCurrentMembers() != null && room.getCurrentMembers() == 0 && (room.getStatus() == null || room.getStatus() == 0)) {
                                room.setStatus(1);
                                roomMapper.updateById(room);
                                roomCacheSyncService.setStatus(roomId, 1);
                            }
                        } catch (Exception ignore) {}
                    }
                } catch (Exception e) {
                    log.error("[房间服务] 发送离开事件失败，房间={}, 用户={}", roomId, userId, e);
                }
            }
        });
        log.info("[房间服务] 用户{}离开房间{}已受理(异步)", userId, roomId);
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
        
        RoomVO roomVO = BeanCopyUtils.copyBean(room, RoomVO.class);
        // 填充房主昵称
        fillOwnerNickname(roomVO, room.getRoomId(), room.getOwnerId());
        return roomVO;
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
        
        log.info("[房间服务] 用户{}获取房间列表成功，共{}个房间", userId, memberRooms.getTotal());

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
            throw new RoomException(RoomErrorCode.NO_PERMISSION);
        }

        // 关闭房间
        room.setStatus(1);
        room.setUpdateTime(LocalDateTime.now());
        int updateRows = roomMapper.updateById(room);

        if (updateRows > 0) {
            log.info("[房间服务] 房间{}已被房主{}关闭", roomId, userId);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try { roomCacheSyncService.setStatus(roomId, 1); } catch (Exception ignore) {}
                }
            });
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
            
            log.info("[房间服务] 房间{}已完全删除", roomId);
            return deleteRows > 0;
        } catch (Exception e) {
            log.error("[房间服务] 删除房间{}失败", roomId, e);
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
                        log.warn("[房间服务] 用户{}在房间{}中的成员信息缺失，使用降级处理", userId, room.getRoomId());
                    }
                }
            } catch (Exception e) {
                log.error("[房间服务] 增强房间{}的用户关系信息失败", room.getRoomId(), e);
                // 设置默认值，不影响主流程
                room.setUserRole(0);
                room.setRelationshipType("joined");
            }
        }
        
        // 补充每个房间的房主昵称
        for (RoomVO room : rooms) {
            fillOwnerNickname(room, room.getRoomId(), room.getOwnerId());
        }

        log.info("[房间服务] 为用户{}增强了{}个房间的关系信息", userId, rooms.size());
    }

    /**
     * 填充房间VO中的房主昵称
     * 从 room_member 表查询房主成员记录的 displayName
     */
    private void fillOwnerNickname(RoomVO roomVO, Long roomId, Long ownerId) {
        if (roomVO == null || roomId == null || ownerId == null) return;
        try {
            LambdaQueryWrapper<RoomMember> qw = new LambdaQueryWrapper<>();
            qw.eq(RoomMember::getRoomId, roomId).eq(RoomMember::getUserId, ownerId);
            RoomMember ownerMember = roomMemberMapper.selectOne(qw);
            if (ownerMember != null && StringUtils.hasText(ownerMember.getDisplayName())) {
                roomVO.setOwnerNickname(ownerMember.getDisplayName());
            }
        } catch (Exception e) {
            log.warn("[房间服务] 查询房主昵称失败 roomId={} ownerId={}", roomId, ownerId);
        }
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
            log.error("[房间服务] 查询用户{}在房间{}中的成员信息失败", userId, roomId, e);
            return null;
        }
    }
    // ==================== 密码相关方法 ====================

    private void verifyRoomPassword(Room room, String inputPassword) {
        int mode = room.getPasswordMode() == null ? 0 : room.getPasswordMode();
        if (mode == 0) return;
        if (!StringUtils.hasText(inputPassword)) {
            throw new RoomException(RoomErrorCode.PASSWORD_REQUIRED);
        }
        String masterKey = roomConfig.getPassword().getMasterKey();
        if (mode == 1) {
            if (!PasswordUtils.verifyPassword(inputPassword, room.getPasswordHash(), room.getRoomId(), masterKey)) {
                throw new RoomException(RoomErrorCode.PASSWORD_WRONG);
            }
        } else if (mode == 2) {
            if (!PasswordUtils.verifyTotp(inputPassword, room.getPasswordHash())) {
                throw new RoomException(RoomErrorCode.PASSWORD_WRONG);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRoomPassword(Long roomId, Long userId, Integer mode, String rawPassword, Integer visible) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND);
        }
        if (!room.getOwnerId().equals(userId)) {
            throw new RoomException(RoomErrorCode.NO_PERMISSION);
        }
        int passwordMode = mode != null ? mode : 0;
        room.setPasswordMode(passwordMode);
        room.setPasswordVisible(visible != null ? visible : 1);
        String masterKey = roomConfig.getPassword().getMasterKey();
        if (passwordMode == 1) {
            if (!StringUtils.hasText(rawPassword)) {
                throw new RoomException(RoomErrorCode.PASSWORD_REQUIRED);
            }
            room.setPasswordHash(PasswordUtils.encryptPassword(rawPassword, roomId, masterKey));
        } else if (passwordMode == 2) {
            room.setPasswordHash(PasswordUtils.generateTotpSecret());
        } else {
            room.setPasswordHash(null);
        }
        room.setUpdateTime(LocalDateTime.now());
        roomMapper.updateById(room);
        final int finalMode = passwordMode;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try { roomCacheSyncService.setPasswordMode(roomId, finalMode); } catch (Exception ignore) {}
            }
        });
        log.info("[房间服务] 房间{}密码已更新，模式={}", roomId, passwordMode);
    }

    @Override
    public RoomVO getRoomCurrentPassword(Long roomId, Long userId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND);
        }
        boolean isOwner = room.getOwnerId().equals(userId);
        boolean isMember = roomMemberService.isMemberInRoom(roomId, userId);
        boolean visibleToMembers = Integer.valueOf(1).equals(room.getPasswordVisible());
        // 房主始终可查；成员仅在 passwordVisible=1 时可查
        if (!isOwner && !(isMember && visibleToMembers)) {
            throw new RoomException(RoomErrorCode.NO_PERMISSION);
        }
        RoomVO vo = new RoomVO();
        vo.setRoomId(room.getRoomId());
        vo.setPasswordMode(room.getPasswordMode());
        vo.setPasswordVisible(room.getPasswordVisible());
        int m = room.getPasswordMode() == null ? 0 : room.getPasswordMode();
        String masterKey = roomConfig.getPassword().getMasterKey();
        if (m == 1 && StringUtils.hasText(room.getPasswordHash())) {
            vo.setCurrentPassword(PasswordUtils.decryptPassword(room.getPasswordHash(), roomId, masterKey));
        } else if (m == 2 && StringUtils.hasText(room.getPasswordHash())) {
            vo.setCurrentPassword(PasswordUtils.getCurrentTotp(room.getPasswordHash()));
            vo.setRemainingSeconds(PasswordUtils.getRemainingSeconds());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void kickMember(Long roomId, Long operatorId, Long targetUserId) {
        if (operatorId == null) {
            throw new RoomException(RoomErrorCode.USER_NOT_LOGGED_IN);
        }
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND);
        }
        if (!room.getOwnerId().equals(operatorId)) {
            throw new RoomException(RoomErrorCode.NO_PERMISSION);
        }
        if (operatorId.equals(targetUserId)) {
            throw new RoomException(RoomErrorCode.NO_PERMISSION);
        }
        if (!roomMemberService.isMemberInRoom(roomId, targetUserId)) {
            throw new RoomException(RoomErrorCode.NOT_IN_ROOM);
        }
        roomMemberService.removeMember(roomId, targetUserId);
        int dec = roomMapper.decrementMembersIfPositive(roomId);
        if (dec == 1) {
            try { roomCacheSyncService.incrementConfirmed(roomId, -1); } catch (Exception ignore) {}
        }
        try { roomCacheSyncService.removeMemberFromCache(roomId, targetUserId); } catch (Exception ignore) {}
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 1. 向房间内所有成员广播成员被踢出事件
                try {
                    wsProducer.sendEventToRoom(roomId, "member_kick", Map.of(
                            "targetUserId", targetUserId,
                            "roomId", roomId
                    ));
                } catch (Exception e) {
                    log.warn("[房间服务] 发送 member_kick 房间事件失败: roomId={}, targetUserId={}", roomId, targetUserId);
                }
                // 2. 向被踢用户的个人频道发送专属踢出通知，确保其必然收到并触发断开逻辑
                try {
                    wsProducer.sendEventToUser(targetUserId, "kicked", Map.of(
                            "roomId", roomId,
                            "operatorId", operatorId
                    ));
                    log.info("[房间服务] 房主{}已将用户{}踢出房间{}", operatorId, targetUserId, roomId);
                } catch (Exception e) {
                    log.warn("[房间服务] 发送 kicked 用户事件失败: roomId={}, targetUserId={}", roomId, targetUserId);
                }
            }
        });
    }
}