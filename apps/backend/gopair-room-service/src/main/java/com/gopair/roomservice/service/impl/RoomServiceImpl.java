package com.gopair.roomservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gopair.common.core.PageResult;

import com.gopair.common.util.BeanCopyUtils;
import com.gopair.roomservice.domain.dto.JoinRoomDto;
import com.gopair.roomservice.domain.dto.RoomDto;
import com.gopair.roomservice.domain.dto.RoomQueryDto;
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
import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.util.RoomCodeUtils;
import com.gopair.roomservice.util.PasswordUtils;
import com.gopair.roomservice.config.RoomConfig;
import com.gopair.framework.logging.annotation.LogRecord;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


/**
 * 房间核心服务实现类
 *
 * 负责房间生命周期管理的所有核心业务，包括创建、加入/离开、关闭/销毁、
 * 密码操作、成员管理等。涉及写DB、写Redis、发MQ、发WebSocket等多系统协作时，
 * 一律使用 {@code afterCommit} 回调，确保事务提交后再触发下游操作。
 *
 * @author gopair
 */
@Slf4j
@Service
@RequiredArgsConstructor
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
    private final RestTemplate restTemplate;
    private static final String FILE_SERVICE_URL = "http://file-service/file/room/";
    private static final String MESSAGE_SERVICE_URL = "http://message-service/message/room/";
    private static final String VOICE_SERVICE_URL = "http://voice-service/voice-call/room/";

    /**
     * 创建新房间，并将创建者自动加入作为房主。
     *
     * * [核心策略]
     * - 密码二次写：固定密码 / TOTP 模式的密码 Hash 依赖真实 roomId 计算，
     *   而 MyBatis-Plus insert 后才回填 id，故需 insert 后再单独 update 一次。
     * - 事务后置：Redis 初始化和 WebSocket 通知必须在事务提交后才执行，
     *   防止 DB 还未落地但下游已感知到脏数据。
     *
     * * [执行链路]
     * 1. 参数校验：userId 防御性校验（来自 UserContextHolder，非请求体传入）。
     * 2. 构造房间：设置房主、初始成员数由 room_member 聚合计算、过期时间、预设密码模式。
     * 3. 生成唯一邀请码：通过 {@code RoomCodeUtils.generateWithRetry} 重试确保不冲突。
     * 4. 插入房间（insert）：MyBatis-Plus 自动回填 roomId。
     * 5. 计算并回写密码 Hash：固定密码走加盐加密，TOTP 走密钥生成（两次写表）。
     * 6. 创建者自动入房：调用 roomMemberService.addMember 写入 room_member（房主角色）。
     * 7. 事务提交后回调：初始化 Redis 缓存 + 发送 room_created 事件（语音服务）。
     *    失败仅 warn，不影响主流程。
     *
     * @param roomDto 创建房间请求
     * @param userId  当前登录用户 ID（来自 UserContextHolder）
     * @return 房间视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "创建房间", module = "房间管理", includeResult = true)
    public RoomVO createRoom(RoomDto roomDto, Long userId) {
        // userId 来自 UserContextHolder（非请求体），需要防御性校验
        if (userId == null) {
            throw new RoomException(RoomErrorCode.USER_NOT_LOGGED_IN);
        }

        // 构造房间实体
        Room room = new Room();
        room.setRoomName(roomDto.getRoomName());
        room.setDescription(roomDto.getDescription());
        // maxMembers 由 DTO 层 @NotNull 保证非空
        room.setMaxMembers(roomDto.getMaxMembers());
        room.setOwnerId(userId);
        room.setStatus(RoomConst.STATUS_ACTIVE);
        room.setVersion(0);

        // expireHours 可选，为空时使用默认值
        int expireHours = roomDto.getExpireHours() != null ? roomDto.getExpireHours() : roomConfig.getDefaultExpireHours();
        room.setExpireTime(LocalDateTime.now().plusHours(expireHours));

        // 生成唯一房间码（内部重试处理碰撞）
        String roomCode = RoomCodeUtils.generateWithRetry(this::isRoomCodeUnique);
        room.setRoomCode(roomCode);

        // 预设密码模式与可见性（insert 前先写入，insert 后再补充 passwordHash）
        int passwordMode = roomDto.getPasswordMode();
        room.setPasswordMode(passwordMode);
        // passwordVisible 可选，为空时默认为 1（显示）
        room.setPasswordVisible(roomDto.getPasswordVisible() != null ? roomDto.getPasswordVisible() : 1);
        // 跨字段条件校验：固定密码模式下 rawPassword 不能为空（注解无法表达，保留在 Service 层）
        if (passwordMode == RoomConst.PASSWORD_MODE_FIXED && !StringUtils.hasText(roomDto.getRawPassword())) {
            throw new RoomException(RoomErrorCode.PASSWORD_REQUIRED);
        }

        // 插入房间（insert 后 MyBatis-Plus 自动回填 roomId）
        if (roomMapper.insert(room) <= 0) {
            throw new RoomException(RoomErrorCode.ROOM_CREATION_FAILED);
        }

        // 创建者自动加入房间（房主角色，insert room_member）
        roomMemberService.addMember(room.getRoomId(), userId, RoomConst.ROLE_OWNER);

        // 插入后回写密码 Hash + currentMembers（合并为一次 updateById）
        String masterKey = roomConfig.getPassword().getMasterKey();
        Long memberCount = roomMemberMapper.selectCount(
                new LambdaQueryWrapper<RoomMember>()
                        .eq(RoomMember::getRoomId, room.getRoomId()));
        room.setCurrentMembers(memberCount.intValue());
        if (passwordMode == RoomConst.PASSWORD_MODE_FIXED) {
            room.setPasswordHash(PasswordUtils.encryptPassword(roomDto.getRawPassword(), room.getRoomId(), masterKey));
        } else if (passwordMode == RoomConst.PASSWORD_MODE_TOTP) {
            room.setPasswordHash(PasswordUtils.generateTotpSecret());
        }
        roomMapper.updateById(room);

        // 转换为 VO 返回
        RoomVO roomVO = BeanCopyUtils.copyBean(room, RoomVO.class);
        log.info("[房间服务] 用户{}创建房间成功，房间ID：{}，房间码：{}", userId, room.getRoomId(), room.getRoomCode());

        // 事务提交后初始化 Redis（含房主与 confirmed=1）
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 初始化 Redis 缓存（成员列表、确认人数等）
                try {
                    roomCacheSyncService.initializeRoomInCache(room, userId);
                } catch (Exception e) {
                    // 缓存初始化失败不影响主业务，仅记录 warn
                    log.warn("[房间服务] Redis 初始化缓存失败 roomId={} 错误={}", room.getRoomId(), e.getMessage());
                }

                // 通知语音服务自动创建通话（失败仅 warn，不影响房间创建）
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

    /**
     * 异步加入房间：通过 Redis 预占机制保证防重、防超卖，返回轮询令牌。
     *
     * * [核心策略]
     * - 异步化：加入请求不直接写 DB，而是通过 Redis 预占（reserved），
     *   由 JoinRoomConsumer 异步消费后才真正写入 DB，保证高并发下不超卖。
     * - 防重：同一用户在同一房间不可重复发起预占。
     *
     * * [执行链路]
     * 1. 参数校验：房间码、userId 必填。
     * 2. 查询房间：校验房间码合法性并确认房间存在。
     * 3. 校验密码：无论同步异步均需密码校验（防止知道房间码即可入房的漏洞）。
     * 4. 尝试预占：调用 joinReservationService.preReserve，Redis Lua 保证原子性。
     * 5. 按预占结果返回：ACCEPTED（已受理）返回 joinToken，ALREADY_JOINED 直接提示，
     *    FULL/CLOSED/EXPIRED 抛异常，其余状态抛通用异常。
     *
     * @param joinRoomDto 加入房间请求（含房间码和密码）
     * @param userId      当前登录用户 ID
     * @return 加入受理结果，包含 joinToken（用于后续轮询）或直接提示信息
     */
    @Override
    @LogRecord(operation = "异步加入房间", module = "房间管理")
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

        verifyRoomPassword(room, joinRoomDto.getPassword());

        if (log.isDebugEnabled()) {
            log.debug("[房间服务][join-async] 异步加入入口 房间码={} 房间ID={} 用户={}", joinRoomDto.getRoomCode(), room.getRoomId(), userId);
        }

        // 尝试预占（Redis Lua 保证原子性，防重、防超卖）
        JoinReservationService.PreReserveResult result = joinReservationService.preReserve(room.getRoomId(), userId);
        switch (result.status) {
            case ACCEPTED:
                return new JoinAcceptedVO(result.joinToken, "已受理");
            case ALREADY_JOINED:
                return new JoinAcceptedVO(null, "已在房间");
            case ALREADY_PROCESSING:
                return new JoinAcceptedVO(null, "已有加入请求正在处理中，请稍候");
            case FULL:
                throw new RoomException(RoomErrorCode.ROOM_FULL);
            case CLOSED:
                throw new RoomException(RoomErrorCode.ROOM_CLOSED);
            case EXPIRED:
                throw new RoomException(RoomErrorCode.ROOM_EXPIRED);
            case SYSTEM_BUSY:
            default:
                throw new RoomException(RoomErrorCode.ROOM_STATE_CHANGED);
        }
    }

    /**
     * 轮询查询异步加入结果，状态由 JoinRoomConsumer 消费后写入 Redis。
     *
     * @param token joinRoomAsync 返回的令牌
     * @return 加入状态（JOINED / PROCESSING / FAILED）
     */
    @Override
    public JoinResultQueryService.JoinStatusVO queryJoinResult(String token) {
        return joinResultQueryService.queryByToken(token);
    }

    /**
     * 离开房间，采用事件化异步处理（MQ）。
     *
     * * [核心策略]
     * - 事务边界：DB 删除和人数递减必须在主流程内完成，保证与房间状态原子一致；
     *   MQ 发送在 afterCommit 中执行；Redis confirmed 同步也在 afterCommit 中执行（避免持有 DB 连接）。
     * - 降级兜底：MQ 发送失败时在 afterCommit 中同步执行 Redis 清理，保证降级路径也能清理缓存。
     *
     * * [执行链路]
     * 1. 权限校验：userId 非空、用户必须在房间内。
     * 2. DB 删除：主流程内执行 room_member 删除和人数递减。
     * 3. afterCommit 回调投递 leave 事件到 MQ。
     *    - 投递成功：Consumer 异步处理后续（Redis confirmed-- 等）。
     *    - 投递失败：降级同步处理，执行 Redis 清理（DB 已由主流程处理完）。
     *
     * @param roomId 房间 ID
     * @param userId 当前登录用户 ID
     * @return true（受理成功）
     */
    @Override
    @LogRecord(operation = "离开房间", module = "房间管理", includeResult = true)
    @Transactional(rollbackFor = Exception.class)
    public boolean leaveRoom(Long roomId, Long userId) {
        if (userId == null) {
            throw new RoomException(RoomErrorCode.USER_NOT_LOGGED_IN);
        }
        if (!roomMemberService.isMemberInRoom(roomId, userId)) {
            throw new RoomException(RoomErrorCode.NOT_IN_ROOM);
        }

        // 软删除：标记成员离开（leave_type=1 主动离开）
        boolean removed = roomMemberService.markAsLeft(roomId, userId, 1);
        if (removed) {
            int dec = roomMapper.decrementMembersIfPositive(roomId);
            if (dec == 1) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            roomCacheSyncService.incrementConfirmed(roomId, -1);
                        } catch (Exception e) {
                            log.warn("[房间服务] Redis confirmed-- 失败 roomId={} 错误={}", roomId, e.getMessage());
                        }
                    }
                });
            }
        }

        String correlationId = UUID.randomUUID().toString();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    LeaveRoomRequestedEvent evt = new LeaveRoomRequestedEvent(roomId, userId, correlationId, System.currentTimeMillis());
                    boolean sent = leaveRoomProducer.sendRequested(evt);
                    if (!sent) {
                        // MQ 发送失败时的降级：Redis 清理（DB 已由主流程处理完）
                        try {
                            roomCacheSyncService.removeMemberFromCache(roomId, userId);
                            Room currentRoom = roomMapper.selectById(roomId);
                            // 如果房间没人自动关闭
                            if (currentRoom != null
                                    && Integer.valueOf(0).equals(currentRoom.getCurrentMembers())
                                    && (currentRoom.getStatus() == null || currentRoom.getStatus() == RoomConst.STATUS_ACTIVE)) {
                                currentRoom.setStatus(RoomConst.STATUS_CLOSED);
                                currentRoom.setClosedTime(LocalDateTime.now());
                                roomMapper.updateById(currentRoom);
                                roomCacheSyncService.setStatus(roomId, RoomConst.STATUS_CLOSED);
                                // 标记所有成员被动离开
                                try {
                                    roomMemberService.markAllAsLeft(roomId, 3);
                                } catch (Exception e) {
                                    log.warn("[房间服务] 自动关闭房间时标记成员离开失败 roomId={} 错误={}", roomId, e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            log.warn("[房间服务] 离开房间降级处理失败 roomId={} userId={} 错误={}", roomId, userId, e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.error("[房间服务] 发送离开事件失败，房间={}, 用户={}", roomId, userId, e);
                }
            }
        });
        log.info("[房间服务] 用户{}离开房间{}已受理(异步)", userId, roomId);
        return true;
    }

    /**
     * 根据房间邀请码查询房间信息。
     *
     * @param roomCode 房间邀请码（6位大写字母）
     * @return 房间视图对象
     */
    @Override
    @LogRecord(operation = "按邀请码查询房间", module = "房间管理")
    public RoomVO getRoomByCode(String roomCode) {
        if (!RoomCodeUtils.isValidFormat(roomCode)) {
            throw new RoomException(RoomErrorCode.ROOM_CODE_INVALID);
        }

        Room room = roomMapper.selectByRoomCode(roomCode);
        if (room == null) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND);
        }

        RoomVO roomVO = BeanCopyUtils.copyBean(room, RoomVO.class);
        return roomVO;
    }

    /**
     * 获取当前用户参与的房间列表（创建的房间 + 加入的房间）。
     * 委托 RoomMemberService.getUserRooms 实现，本方法不承担具体查询逻辑。
     *
     * * [执行链路]
     * 1. userId 防御性校验。
     * 2. 委托 roomMemberService.getUserRooms 获取分页结果。
     * 3. 返回结果。
     *
     * @param userId 当前登录用户 ID
     * @param query  查询条件（分页、状态过滤等）
     * @return 用户参与的房间分页结果
     */
    @Override
    @LogRecord(operation = "查询用户房间列表", module = "房间管理")
    public PageResult<RoomVO> getUserRooms(Long userId, RoomQueryDto query) {
        if (userId == null) {
            throw new RoomException(RoomErrorCode.USER_NOT_LOGGED_IN);
        }

        PageResult<RoomVO> result = roomMemberService.getUserRooms(userId, query);

        log.info("[房间服务] 用户{}获取房间列表成功，共{}个房间", userId, result.getTotal());
        return result;
    }

    /**
     * 关闭房间（仅房主可操作），关闭后房间状态变为 CLOSED，不再接受新成员。
     *
     * * [核心策略]
     * - 软关闭：仅修改 room.status = CLOSED，不删除数据，保留历史记录。
     * - 事务后置：Redis 缓存更新和 WebSocket 广播必须在事务提交后执行。
     *
     * * [执行链路]
     * 1. 权限校验：只有房主可关闭房间。
     * 2. 更新状态：将 room.status = CLOSED。
     * 3. 事务提交后回调：
     *    - 更新 Redis 缓存状态。
     *    - 删除 Redis 中的房间成员列表（防止缓存数据残留）。
     *    - 广播 room_closed 事件，通知房间内所有成员。
     *
     * @param roomId 房间 ID
     * @param userId 当前登录用户 ID（必须是房主）
     * @return true 表示关闭成功
     */
    @Override
    @LogRecord(operation = "关闭房间", module = "房间管理", includeResult = true)
    @Transactional(rollbackFor = Exception.class)
    public boolean closeRoom(Long roomId, Long userId) {
        if (userId == null) {
            throw new RoomException(RoomErrorCode.USER_NOT_LOGGED_IN);
        }

        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND);
        }

        // 防止重复关闭
        if (room.getStatus() != null && room.getStatus() == RoomConst.STATUS_CLOSED) {
            throw new RoomException(RoomErrorCode.ROOM_ALREADY_CLOSED);
        }

        // 只有房主可以关闭房间
        if (!room.getOwnerId().equals(userId)) {
            throw new RoomException(RoomErrorCode.NO_PERMISSION);
        }

        // 软关闭：更新状态并记录关闭时间
        room.setStatus(RoomConst.STATUS_CLOSED);
        room.setClosedTime(LocalDateTime.now());
        int updateRows = roomMapper.updateById(room);

        if (updateRows > 0) {
            log.info("[房间服务] 房间{}已被房主{}关闭", roomId, userId);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 标记所有仍在房间的成员为被动离开（leave_type=3）
                    try {
                        roomMemberService.markAllAsLeft(roomId, 3);
                    } catch (Exception e) {
                        log.warn("[房间服务] 标记房间成员离开失败 roomId={} 错误={}", roomId, e.getMessage());
                    }
                    // 更新 Redis 缓存中的房间状态
                    try {
                        roomCacheSyncService.setStatus(roomId, RoomConst.STATUS_CLOSED);
                    } catch (Exception e) {
                        log.warn("[房间服务] Redis 更新房间状态失败 roomId={} 错误={}", roomId, e.getMessage());
                    }
                    // 删除 Redis 成员列表缓存（防止关闭后仍可读到旧成员）
                    try {
                        stringRedisTemplate.delete(RoomConst.membersKey(roomId));
                    } catch (Exception e) {
                        log.warn("[房间服务] Redis 删除房间成员失败 roomId={} 错误={}", roomId, e.getMessage());
                    }
                    // 广播关闭事件，通知房间内所有成员
                    try {
                        wsProducer.sendEventToRoom(roomId, "room_closed", Map.of(
                            "roomId", roomId,
                            "operatorId", userId
                        ));
                    } catch (Exception e) {
                        log.warn("[房间服务] 发送 room_closed 事件失败: roomId={}, operatorId={}", roomId, userId);
                    }
                }
            });
        }

        return updateRows > 0;
    }

    /**
     * 获取指定房间的所有成员列表（进入房间详情页时调用）。
     *
     * @param roomId 房间 ID
     * @return 成员信息列表
     */
    @Override
    @LogRecord(operation = "查询房间成员", module = "房间管理")
    public List<RoomMemberVO> getRoomMembers(Long roomId) {
        if (roomId == null) {
            throw new RoomException(RoomErrorCode.PARAM_INVALID);
        }

        return roomMemberService.getRoomMembers(roomId);
    }

    /**
     * 查询所有已过期的房间（定时任务调用）。
     *
     * @return 已过期且状态仍为活跃的房间列表
     */
    @Override
    @LogRecord(operation = "查询过期房间", module = "房间管理")
    public List<Room> findExpiredRooms() {
        return roomMapper.selectExpiredRooms(LocalDateTime.now());
    }

    /**
     * 查询需要清理的房间：已关闭超过24小时。
     *
     * @return 待清理房间列表（按关闭时间升序，防止并发清理冲突）
     */
    @Override
    @LogRecord(operation = "查询待清理房间", module = "房间管理")
    public List<Room> findRoomsToClean() {
        return roomMapper.selectRoomsToClean(LocalDateTime.now().minusHours(24));
    }

    /**
     * 清理房间资源（消息、文件、语音通话）。
     * room 和 room_member 永久保留，不在此方法中删除。
     *
     * * [执行链路]
     * 1. 清理消息：HTTP 调用 message-service /message/room/{roomId}/cleanup
     * 2. 清理文件：HTTP 调用 file-service /file/room/{roomId}/cleanup（已实现）
     * 3. 清理语音通话：HTTP 调用 voice-service /voice-call/room/{roomId}/cleanup
     *
     * @param roomId 房间ID
     * @return 实际清理的记录总数
     */
    @Override
    @LogRecord(operation = "清理房间资源", module = "定时任务", includeResult = true)
    public int cleanupRoomResources(Long roomId) {
        int total = 0;

        // 1. 清理消息
        try {
            Integer count = restTemplate.postForObject(MESSAGE_SERVICE_URL + roomId + "/cleanup", null, Integer.class);
            if (count != null) {
                total += count;
            }
            log.info("[房间服务] 消息清理完成 roomId={} count={}", roomId, count);
        } catch (Exception e) {
            log.warn("[房间服务] 清理房间{}消息失败: {}", roomId, e.getMessage());
        }

        // 2. 清理文件
        try {
            Integer count = restTemplate.postForObject(FILE_SERVICE_URL + roomId + "/cleanup", null, Integer.class);
            if (count != null) {
                total += count;
            }
            log.info("[房间服务] 文件清理完成 roomId={} count={}", roomId, count);
        } catch (Exception e) {
            log.warn("[房间服务] 清理房间{}文件失败: {}", roomId, e.getMessage());
        }

        // 3. 清理语音通话
        try {
            Integer count = restTemplate.postForObject(VOICE_SERVICE_URL + roomId + "/cleanup", null, Integer.class);
            if (count != null) {
                total += count;
            }
            log.info("[房间服务] 语音通话清理完成 roomId={} count={}", roomId, count);
        } catch (Exception e) {
            log.warn("[房间服务] 清理房间{}语音通话失败: {}", roomId, e.getMessage());
        }

        return total;
    }

    /**
     * 完全删除房间。
     * 注意：此方法已不再被定时任务调用（room_member 永久保留）。
     * 仅保留用于极端清理场景（如 GDPR 合规数据删除）。
     *
     * @param roomId 房间 ID
     * @return true 表示删除成功
     */
    @Override
    @LogRecord(operation = "删除房间", module = "房间管理", includeResult = true)
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRoomCompletely(Long roomId) {
        try {
            // 不再删除 room_member（永久保留以供审计追踪）
            // 删除房间记录
            int deleteRows = roomMapper.deleteById(roomId);

            if (deleteRows > 0) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            stringRedisTemplate.delete(RoomConst.metaKey(roomId));
                            stringRedisTemplate.delete(RoomConst.membersKey(roomId));
                            stringRedisTemplate.delete(RoomConst.pendingKey(roomId));
                            log.info("[房间服务] 房间{}的 Redis 缓存已清除", roomId);
                        } catch (Exception e) {
                            log.warn("[房间服务] 清除房间{}的 Redis 缓存失败", roomId, e);
                        }
                    }
                });
            }

            log.info("[房间服务] 房间{}已完全删除", roomId);
            return deleteRows > 0;
        } catch (Exception e) {
            log.error("[房间服务] 删除房间{}失败", roomId, e);
            return false;
        }
    }

    /**
     * 校验房间邀请码是否唯一（RoomCodeUtils.generateWithRetry 的回调）。
     *
     * @param roomCode 待校验的邀请码
     * @return true 表示可用（未被占用）
     */
    @Override
    @LogRecord(operation = "校验邀请码唯一性", module = "房间管理")
    public boolean isRoomCodeUnique(String roomCode) {
        Room room = roomMapper.selectByRoomCode(roomCode);
        return room == null;
    }

    // ==================== 密码相关方法 ====================

    /**
     * 校验用户输入的房间密码（固定密码或 TOTP 动态令牌）。
     *
     * * [执行链路]
     * 1. 无密码模式（passwordMode=0）：直接通过，不校验。
     * 2. 空密码拦截：密码非空模式下若未传密码则抛异常。
     * 3. 固定密码：调用 PasswordUtils.verifyPassword（BCrypt 风格比对）。
     * 4. TOTP 令牌：调用 PasswordUtils.verifyTotp（时间步长 TOTP 校验）。
     *
     * @param room          房间实体
     * @param inputPassword 用户输入的密码
     */
    private void verifyRoomPassword(Room room, String inputPassword) {
        // 空值保护：null 视为 NONE 模式
        int mode = room.getPasswordMode() == null ? RoomConst.PASSWORD_MODE_NONE : room.getPasswordMode();
        if (mode == RoomConst.PASSWORD_MODE_NONE) return;
        if (!StringUtils.hasText(inputPassword)) {
            throw new RoomException(RoomErrorCode.PASSWORD_REQUIRED);
        }
        String masterKey = roomConfig.getPassword().getMasterKey();
        if (mode == RoomConst.PASSWORD_MODE_FIXED) {
            if (!PasswordUtils.verifyPassword(inputPassword, room.getPasswordHash(), room.getRoomId(), masterKey)) {
                throw new RoomException(RoomErrorCode.PASSWORD_WRONG);
            }
        } else if (mode == RoomConst.PASSWORD_MODE_TOTP) {
            if (!PasswordUtils.verifyTotp(inputPassword, room.getPasswordHash())) {
                throw new RoomException(RoomErrorCode.PASSWORD_WRONG);
            }
        }
    }

    /**
     * 更新房间密码模式（仅房主可操作）。
     *
     * * [执行链路]
     * 1. 权限校验：只有房主可修改。
     * 2. 更新密码模式和可见性。
     * 3. 根据模式计算并回写密码 Hash：
     *    - NONE：清空密码。
     *    - FIXED：加盐加密后回写。
     *    - TOTP：生成新密钥回写。
     * 4. 事务提交后：同步更新 Redis 缓存中的密码模式。
     *
     * @param roomId      房间 ID
     * @param userId      当前登录用户 ID（必须是房主）
     * @param mode        新密码模式（可为 null，表示不修改模式）
     * @param rawPassword 原始密码（FOLLOW_MODES_FIXED 时必填）
     * @param visible     密码是否对成员可见（可为 null，为 null 则不修改）
     */
    @Override
    @LogRecord(operation = "更新房间密码", module = "房间管理")
    @Transactional(rollbackFor = Exception.class)
    public void updateRoomPassword(Long roomId, Long userId, Integer mode, String rawPassword, Integer visible) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND);
        }
        if (!room.getOwnerId().equals(userId)) {
            throw new RoomException(RoomErrorCode.NO_PERMISSION);
        }

        int passwordMode = mode != null ? mode : RoomConst.PASSWORD_MODE_NONE;
        room.setPasswordMode(passwordMode);
        if (visible != null) {
            room.setPasswordVisible(visible);
        }

        String masterKey = roomConfig.getPassword().getMasterKey();
        if (passwordMode == RoomConst.PASSWORD_MODE_FIXED) {
            if (!StringUtils.hasText(rawPassword)) {
                throw new RoomException(RoomErrorCode.PASSWORD_REQUIRED);
            }
            room.setPasswordHash(PasswordUtils.encryptPassword(rawPassword, roomId, masterKey));
        } else if (passwordMode == RoomConst.PASSWORD_MODE_TOTP) {
            room.setPasswordHash(PasswordUtils.generateTotpSecret());
        } else {
            room.setPasswordHash(null);
        }

        roomMapper.updateById(room);

        // 事务提交后同步 Redis 缓存
        final int finalMode = passwordMode;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    roomCacheSyncService.setPasswordMode(roomId, finalMode);
                } catch (Exception e) {
                    log.warn("[房间服务] Redis 更新密码模式失败 roomId={} 错误={}", roomId, e.getMessage());
                }
            }
        });
        log.info("[房间服务] 房间{}密码已更新，模式={}", roomId, passwordMode);
    }

    /**
     * 更新房间密码可见性（仅切换可见性，不涉及密码模式变更）。
     *
     * * [执行链路]
     * 1. 查询房间并校验存在性。
     * 2. 校验操作者是否为房主。
     * 3. 更新 passwordVisible 字段。
     *
     * @param roomId  房间 ID
     * @param userId  当前登录用户 ID（必须是房主）
     * @param visible 密码是否对成员可见（0-隐藏 1-显示）
     */
    @Override
    @LogRecord(operation = "更新房间密码可见性", module = "房间管理")
    @Transactional(rollbackFor = Exception.class)
    public void updatePasswordVisibility(Long roomId, Long userId, Integer visible) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND);
        }
        if (!room.getOwnerId().equals(userId)) {
            throw new RoomException(RoomErrorCode.NO_PERMISSION);
        }

        room.setPasswordVisible(visible);
        roomMapper.updateById(room);

        log.info("[房间服务] 房间{}密码可见性已更新，visible={}", roomId, visible);
    }

    /**
     * 获取房间当前密码/令牌（房主始终可查，成员仅在 passwordVisible=1 时可查）。
     *
     * * [执行链路]
     * 1. 权限判断：房主始终可查；成员仅在 passwordVisible=1 时可查。
     * 2. FIXED 模式：解密返回密码明文。
     * 3. TOTP 模式：返回当前时刻的动态口令明文和剩余有效秒数。
     *
     * @param roomId 房间 ID
     * @param userId 当前登录用户 ID
     * @return 包含密码模式、明文、剩余秒数等信息的 VO
     */
    @Override
    @LogRecord(operation = "查询房间当前密码", module = "房间管理")
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

        int m = room.getPasswordMode() == null ? RoomConst.PASSWORD_MODE_NONE : room.getPasswordMode();
        String masterKey = roomConfig.getPassword().getMasterKey();
        if (m == RoomConst.PASSWORD_MODE_FIXED && StringUtils.hasText(room.getPasswordHash())) {
            vo.setCurrentPassword(PasswordUtils.decryptPassword(room.getPasswordHash(), roomId, masterKey));
        } else if (m == RoomConst.PASSWORD_MODE_TOTP && StringUtils.hasText(room.getPasswordHash())) {
            vo.setCurrentPassword(PasswordUtils.getCurrentTotp(room.getPasswordHash()));
            vo.setRemainingSeconds(PasswordUtils.getRemainingSeconds());
        }

        return vo;
    }

    /**
     * 房主踢出指定成员。
     *
     * * [执行链路]
     * 1. 权限校验：userId 非空、仅房主可踢人、不能踢自己。
     * 2. 目标存在性校验：被踢用户必须在房间内。
     * 3. 删除成员 + 原子减人数（decrementMembersIfPositive，防止负数）。
     * 4. 事务提交后回调（仅在真实删除了成员时才执行）：
     *    - 同步 Redis confirmed 人数（-1）。
     *    - 从 Redis 缓存中移除该成员。
     *    - 广播 member_kick 事件（通知房间内所有人）。
     *    - 向被踢用户个人发送 kicked 事件（触发其 WebSocket 断开，保证强一致性）。
     *
     * @param roomId      房间 ID
     * @param operatorId  操作者 ID（必须是房主）
     * @param targetUserId 被踢用户 ID
     */
    @Override
    @LogRecord(operation = "踢出房间成员", module = "房间管理")
    @Transactional(rollbackFor = Exception.class)
    public void kickMember(Long roomId, Long operatorId, Long targetUserId) {
        if (operatorId == null) {
            throw new RoomException(RoomErrorCode.USER_NOT_LOGGED_IN);
        }
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND);
        }
        // 房主才可踢人，且不能踢自己
        if (!room.getOwnerId().equals(operatorId)) {
            throw new RoomException(RoomErrorCode.NO_PERMISSION);
        }
        if (operatorId.equals(targetUserId)) {
            throw new RoomException(RoomErrorCode.NO_PERMISSION);
        }
        if (!roomMemberService.isMemberInRoom(roomId, targetUserId)) {
            throw new RoomException(RoomErrorCode.NOT_IN_ROOM);
        }

        // 软删除：标记成员离开（leave_type=2 被踢出） + 原子减人数
        roomMemberService.markAsLeft(roomId, targetUserId, 2);
        int dec = roomMapper.decrementMembersIfPositive(roomId);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 仅在真实删除了成员后（dec == 1）才更新 Redis confirmed 人数
                if (dec == 1) {
                    try {
                        roomCacheSyncService.incrementConfirmed(roomId, -1);
                    } catch (Exception e) {
                        log.warn("[房间服务] Redis 更新确认成员数失败 roomId={} 错误={}", roomId, e.getMessage());
                    }
                }
                // 从 Redis 缓存中移除该成员
                try {
                    roomCacheSyncService.removeMemberFromCache(roomId, targetUserId);
                } catch (Exception e) {
                    log.warn("[房间服务] Redis 移除成员失败 roomId={} targetUserId={} 错误={}", roomId, targetUserId, e.getMessage());
                }
                // 广播 member_kick 事件，通知房间内所有在线成员
                try {
                    wsProducer.sendEventToRoom(roomId, "member_kick", Map.of(
                            "targetUserId", targetUserId,
                            "roomId", roomId
                    ));
                } catch (Exception e) {
                    log.warn("[房间服务] 发送 member_kick 房间事件失败: roomId={}, targetUserId={}", roomId, targetUserId);
                }
                // 向被踢用户的个人频道发送 kicked 事件，触发其 WebSocket 断开（房间事件可能被错过，私发保证送达）
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

    /**
     * 判断用户是否为指定房间的成员（委托给 RoomMemberService）。
     *
     * @param roomId 房间 ID
     * @param userId 用户 ID
     * @return true 表示用户在房间中
     */
    @Override
    @LogRecord(operation = "查询用户是否在房间", module = "房间管理")
    public boolean isMemberInRoom(Long roomId, Long userId) {
        if (roomId == null || userId == null) {
            return false;
        }
        return roomMemberService.isMemberInRoom(roomId, userId);
    }
}
