package com.gopair.roomservice.service;

import com.gopair.common.core.PageResult;
import com.gopair.roomservice.base.BaseIntegrationTest;
import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.enums.RoomErrorCode;
import com.gopair.roomservice.domain.dto.JoinRoomDto;
import com.gopair.roomservice.domain.dto.RoomDto;
import com.gopair.roomservice.domain.dto.RoomQueryDto;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.domain.po.RoomMember;
import com.gopair.roomservice.domain.vo.JoinAcceptedVO;
import com.gopair.roomservice.domain.vo.RoomMemberVO;
import com.gopair.roomservice.domain.vo.RoomPasswordVO;
import com.gopair.roomservice.domain.vo.RoomVO;
import com.gopair.roomservice.exception.RoomException;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import com.gopair.roomservice.service.impl.RoomMemberServiceImpl;
import com.gopair.roomservice.service.impl.RoomServiceImpl;
import com.gopair.roomservice.util.PasswordUtils;
import com.gopair.roomservice.config.RoomConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 房间服务生命周期集成测试 — Service 层。
 *
 * * [核心策略]
 * - 真实 DB + 真实 Redis：通过 @Autowired 直接注入 Service/Mapper，验证业务逻辑。
 * - @Transactional：MySQL 自动回滚，@AfterEach flushDb() 清理 Redis。
 * - 测试流覆盖：创建→入房→查询→修改→离开→状态变更 等关键链路。
 *
 * * [Mock 范围]
 * - MQ Consumer/Producer/WebSocket：已在 BaseIntegrationTest Mock，避免异步消费干扰。
 * - 用户资料查询：Mock 的 RestTemplate 返回 null，降级为「用户{userId}」昵称。
 *
 * @author gopair
 */
@Slf4j
@DisplayName("房间服务生命周期集成测试")
class RoomServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RoomServiceImpl roomService;

    @Autowired
    private RoomMemberServiceImpl roomMemberService;

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private RoomMemberMapper roomMemberMapper;

    @Autowired
    private RoomConfig roomConfig;

    @Autowired
    private com.gopair.roomservice.service.RoomCacheSyncService roomCacheSyncService;

    private static final AtomicLong counter = new AtomicLong(System.currentTimeMillis() % 100_000_000);

    private long uid() {
        return counter.incrementAndGet();
    }

    private RoomVO createRoomWithCache(RoomDto dto, Long ownerId) {
        RoomVO room = roomService.createRoom(dto, ownerId);
        Room dbRoom = roomMapper.selectById(room.getRoomId());
        roomCacheSyncService.initializeRoomInCache(dbRoom, ownerId);
        return room;
    }

    private void addMemberWithCache(Long roomId, Long userId, Integer role) {
        roomMemberService.addMember(roomId, userId, role);
        roomMapper.incrementMembersIfNotFull(roomId);
        roomCacheSyncService.addMemberToCache(roomId, userId);
        roomCacheSyncService.incrementConfirmed(roomId, 1);
    }

    // ========================================================================
    // 测试流 A：房间基础生命周期
    // ========================================================================

    @Nested
    @DisplayName("测试流 A：房间基础生命周期（无密码模式）")
    class RoomBasicLifecycleFlow {

        @Test
        @DisplayName("Step 1: 创建房间 → DB 记录正确，状态 ACTIVE")
        void createRoom_ShouldPersistCorrectly() {
            Long ownerId = uid();
            RoomDto dto = buildRoomDto("测试房间_" + uid(), RoomConst.PASSWORD_MODE_NONE);

            RoomVO room = roomService.createRoom(dto, ownerId);

            assertThat(room.getRoomId()).isNotNull();
            assertThat(room.getRoomName()).isEqualTo(dto.getRoomName());
            assertThat(room.getMaxMembers()).isEqualTo(10);
            assertThat(room.getStatus()).isEqualTo(RoomConst.STATUS_ACTIVE);
            assertThat(room.getOwnerId()).isEqualTo(ownerId);
            assertThat(room.getCurrentMembers()).isEqualTo(1);
            assertThat(room.getRoomCode()).hasSize(8);

            Room dbRoom = roomMapper.selectById(room.getRoomId());
            assertThat(dbRoom).isNotNull();
            assertThat(dbRoom.getRoomName()).isEqualTo(dto.getRoomName());
            assertThat(dbRoom.getPasswordMode()).isEqualTo(RoomConst.PASSWORD_MODE_NONE);
        }

        @Test
        @DisplayName("Step 2: 按房间码查询 → 返回完整房间信息")
        void getRoomByCode_ShouldReturnCorrectRoom() {
            Long userId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("按码查询_" + uid(), RoomConst.PASSWORD_MODE_NONE), userId);

            RoomVO found = roomService.getRoomByCode(room.getRoomCode());

            assertThat(found.getRoomId()).isEqualTo(room.getRoomId());
            assertThat(found.getRoomName()).isEqualTo(room.getRoomName());
            assertThat(found.getStatus()).isEqualTo(RoomConst.STATUS_ACTIVE);
        }

        @Test
        @DisplayName("Step 3: 创建时自动入房 → DB 中房主已存在")
        void createRoom_ShouldAutoAddOwnerAsMember() {
            Long userId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("自动入房_" + uid(), RoomConst.PASSWORD_MODE_NONE), userId);

            RoomMember member = roomMemberMapper.selectList(
                    new LambdaQueryWrapper<RoomMember>()
                            .eq(RoomMember::getRoomId, room.getRoomId())
                            .eq(RoomMember::getUserId, userId)
            ).stream().findFirst().orElse(null);

            assertThat(member).isNotNull();
            assertThat(member.getRole()).isEqualTo(RoomConst.ROLE_OWNER);
            assertThat(member.getStatus()).isEqualTo(RoomConst.MEMBER_STATUS_ONLINE);
            assertThat(member.getJoinTime()).isNotNull();
        }

        @Test
        @DisplayName("Step 4: 查询房间成员 → 包含房主信息")
        void getRoomMembers_ShouldIncludeOwner() {
            Long ownerId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("成员查询_" + uid(), RoomConst.PASSWORD_MODE_NONE), ownerId);

            List<RoomMemberVO> members = roomService.getRoomMembers(room.getRoomId());

            assertThat(members).hasSize(1);
            RoomMemberVO ownerMember = members.get(0);
            assertThat(ownerMember.getUserId()).isEqualTo(ownerId);
            assertThat(ownerMember.getIsOwner()).isTrue();
            assertThat(ownerMember.getNickname()).isNotNull();
        }

        @Test
        @DisplayName("Step 5: 查询用户房间列表 → 包含 relationshipType=created")
        void getUserRooms_ShouldEnrichWithRelationship() {
            Long userId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("关系测试_" + uid(), RoomConst.PASSWORD_MODE_NONE), userId);

            RoomQueryDto query = new RoomQueryDto();
            query.setPageNum(1);
            query.setPageSize(10);
            PageResult<RoomVO> result = roomService.getUserRooms(userId, query);

            assertThat(result.getTotal()).isGreaterThanOrEqualTo(1);
            RoomVO enriched = result.getRecords().get(0);
            assertThat(enriched.getRoomId()).isEqualTo(room.getRoomId());
            assertThat(enriched.getRelationshipType()).isEqualTo("created");
            assertThat(enriched.getUserRole()).isEqualTo(RoomConst.ROLE_OWNER);
            assertThat(enriched.getJoinTime()).isNotNull();
        }

        @Test
        @DisplayName("Step 6: 主动离开房间 → DB 成员删除 + 人数减一")
        void leaveRoom_ShouldDeleteMemberAndDecrementCount() {
            Long ownerId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("离开测试_" + uid(), RoomConst.PASSWORD_MODE_NONE), ownerId);

            Room before = roomMapper.selectById(room.getRoomId());
            assertThat(before.getCurrentMembers()).isEqualTo(1);

            boolean result = roomService.leaveRoom(room.getRoomId(), ownerId);
            assertThat(result).isTrue();

            RoomMember member = roomMemberMapper.selectList(
                    new LambdaQueryWrapper<RoomMember>()
                            .eq(RoomMember::getRoomId, room.getRoomId())
                            .eq(RoomMember::getUserId, ownerId)
            ).stream().findFirst().orElse(null);
            assertThat(member).isNull();
        }

        @Test
        @DisplayName("Step 7: 用户不在房间内 → 离开时抛 NOT_IN_ROOM")
        void leaveRoom_WhenNotInRoom_ShouldThrow() {
            Long ownerId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("不在房间_" + uid(), RoomConst.PASSWORD_MODE_NONE), ownerId);
            Long strangerId = uid();

            RoomException ex = assertThrows(RoomException.class,
                    () -> roomService.leaveRoom(room.getRoomId(), strangerId));
            assertThat(ex.getErrorCode().getCode()).isEqualTo(RoomErrorCode.NOT_IN_ROOM.getCode());
        }
    }

    // ========================================================================
    // 测试流 B：固定密码 + 踢人
    // ========================================================================

    @Nested
    @DisplayName("测试流 B：固定密码房间 + 踢人权限")
    class RoomPasswordAndKickFlow {

        @Test
        @DisplayName("Step 1: 创建固定密码房间 → 密码加密存储")
        void createRoom_WithFixedPassword_ShouldEncryptAndStore() {
            Long ownerId = uid();
            String rawPassword = "TestPass_" + uid();
            RoomDto dto = buildRoomDto("密码房间_" + uid(), RoomConst.PASSWORD_MODE_FIXED);
            dto.setRawPassword(rawPassword);
            dto.setPasswordVisible(1);

            RoomVO room = roomService.createRoom(dto, ownerId);

            assertThat(room.getPasswordMode()).isEqualTo(RoomConst.PASSWORD_MODE_FIXED);
            Room dbRoom = roomMapper.selectById(room.getRoomId());
            assertThat(dbRoom.getPasswordHash()).isNotNull();
            assertThat(dbRoom.getPasswordHash()).isNotEqualTo(rawPassword);

            String decrypted = PasswordUtils.decryptPassword(
                    dbRoom.getPasswordHash(), room.getRoomId(), roomConfig.getPassword().getMasterKey());
            assertThat(decrypted).isEqualTo(rawPassword);
        }

        @Test
        @DisplayName("Step 2: 错误密码入房被拒 → 抛 PASSWORD_WRONG")
        void joinRoom_WithWrongPassword_ShouldThrow() {
            Long ownerId = uid();
            String correctPassword = "CorrectPass_" + uid();
            Long memberId = uid();

            RoomVO room = roomService.createRoom(
                    buildRoomDto("密码校验_" + uid(), RoomConst.PASSWORD_MODE_FIXED, correctPassword), ownerId);

            JoinRoomDto joinDto = new JoinRoomDto();
            joinDto.setRoomCode(room.getRoomCode());
            joinDto.setPassword("WrongPassword_" + uid());

            RoomException ex = assertThrows(RoomException.class,
                    () -> roomService.joinRoomAsync(joinDto, memberId));
            assertThat(ex.getErrorCode().getCode()).isEqualTo(RoomErrorCode.PASSWORD_WRONG.getCode());
        }

        @Test
        @DisplayName("Step 3: 房主踢人 → 成员记录删除")
        void kickMember_ShouldDeleteMemberAndDecrementCount() {
            Long ownerId = uid();
            Long memberId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("踢人测试_" + uid(), RoomConst.PASSWORD_MODE_NONE), ownerId);

            addMemberWithCache(room.getRoomId(), memberId, RoomConst.ROLE_MEMBER);

            Room before = roomMapper.selectById(room.getRoomId());
            assertThat(before.getCurrentMembers()).isEqualTo(2);

            roomService.kickMember(room.getRoomId(), ownerId, memberId);

            RoomMember kicked = roomMemberMapper.selectList(
                    new LambdaQueryWrapper<RoomMember>()
                            .eq(RoomMember::getRoomId, room.getRoomId())
                            .eq(RoomMember::getUserId, memberId)
            ).stream().findFirst().orElse(null);
            assertThat(kicked).isNull();
        }

        @Test
        @DisplayName("Step 4: 非房主踢人 → 抛 NO_PERMISSION")
        void kickMember_ByNonOwner_ShouldThrow() {
            Long ownerId = uid();
            Long strangerId = uid();
            Long victimId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("权限测试_" + uid(), RoomConst.PASSWORD_MODE_NONE), ownerId);

            RoomException ex = assertThrows(RoomException.class,
                    () -> roomService.kickMember(room.getRoomId(), strangerId, victimId));
            assertThat(ex.getErrorCode().getCode()).isEqualTo(RoomErrorCode.NO_PERMISSION.getCode());
        }

        @Test
        @DisplayName("Step 5: 房主自踢 → 抛 NO_PERMISSION")
        void kickMember_OwnerKickSelf_ShouldThrow() {
            Long ownerId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("自踢测试_" + uid(), RoomConst.PASSWORD_MODE_NONE), ownerId);

            RoomException ex = assertThrows(RoomException.class,
                    () -> roomService.kickMember(room.getRoomId(), ownerId, ownerId));
            assertThat(ex.getErrorCode().getCode()).isEqualTo(RoomErrorCode.NO_PERMISSION.getCode());
        }

        @Test
        @DisplayName("Step 6: 关闭房间 → 状态变为 CLOSED")
        void closeRoom_ShouldSetStatusToClosed() {
            Long ownerId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("关闭测试_" + uid(), RoomConst.PASSWORD_MODE_NONE), ownerId);

            assertThat(room.getStatus()).isEqualTo(RoomConst.STATUS_ACTIVE);
            boolean result = roomService.closeRoom(room.getRoomId(), ownerId);
            assertThat(result).isTrue();

            Room dbRoom = roomMapper.selectById(room.getRoomId());
            assertThat(dbRoom.getStatus()).isEqualTo(RoomConst.STATUS_CLOSED);
        }

        @Test
        @DisplayName("Step 7: 非房主关闭房间 → 抛 NO_PERMISSION")
        void closeRoom_ByNonOwner_ShouldThrow() {
            Long ownerId = uid();
            Long strangerId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("关闭权限_" + uid(), RoomConst.PASSWORD_MODE_NONE), ownerId);

            RoomException ex = assertThrows(RoomException.class,
                    () -> roomService.closeRoom(room.getRoomId(), strangerId));
            assertThat(ex.getErrorCode().getCode()).isEqualTo(RoomErrorCode.NO_PERMISSION.getCode());
        }
    }

    // ========================================================================
    // 测试流 C：修改密码
    // ========================================================================

    @Nested
    @DisplayName("测试流 C：修改房间密码")
    class UpdatePasswordFlow {

        @Test
        @DisplayName("房主修改密码模式 NONE → FIXED")
        void updatePassword_FromNoneToFixed_ShouldEncryptAndStore() {
            Long ownerId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("改密测试_" + uid(), RoomConst.PASSWORD_MODE_NONE), ownerId);
            String newPassword = "NewSecurePass_" + uid();

            roomService.updateRoomPassword(
                    room.getRoomId(), ownerId, RoomConst.PASSWORD_MODE_FIXED, newPassword, 1);

            Room dbRoom = roomMapper.selectById(room.getRoomId());
            assertThat(dbRoom.getPasswordMode()).isEqualTo(RoomConst.PASSWORD_MODE_FIXED);
            assertThat(dbRoom.getPasswordHash()).isNotNull();

            String decrypted = PasswordUtils.decryptPassword(
                    dbRoom.getPasswordHash(), room.getRoomId(), roomConfig.getPassword().getMasterKey());
            assertThat(decrypted).isEqualTo(newPassword);
        }

        @Test
        @DisplayName("非房主修改密码 → 抛 NO_PERMISSION")
        void updatePassword_ByNonOwner_ShouldThrow() {
            Long ownerId = uid();
            Long strangerId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("改密权限_" + uid(), RoomConst.PASSWORD_MODE_NONE), ownerId);

            RoomException ex = assertThrows(RoomException.class,
                    () -> roomService.updateRoomPassword(
                            room.getRoomId(), strangerId, RoomConst.PASSWORD_MODE_FIXED, "pass1234", 1));
            assertThat(ex.getErrorCode().getCode()).isEqualTo(RoomErrorCode.NO_PERMISSION.getCode());
        }

        @Test
        @DisplayName("TOTP 模式生成随机密钥")
        void createRoom_WithTotpMode_ShouldGenerateSecret() {
            Long ownerId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("TOTP测试_" + uid(), RoomConst.PASSWORD_MODE_TOTP), ownerId);

            assertThat(room.getPasswordMode()).isEqualTo(RoomConst.PASSWORD_MODE_TOTP);
            Room dbRoom = roomMapper.selectById(room.getRoomId());
            assertThat(dbRoom.getPasswordHash()).isNotNull();
            assertThat(dbRoom.getPasswordHash().length()).isGreaterThan(10);

            String currentCode = PasswordUtils.getCurrentTotp(dbRoom.getPasswordHash());
            assertThat(currentCode).isNotNull();
            assertThat(PasswordUtils.verifyTotp(currentCode, dbRoom.getPasswordHash())).isTrue();
        }
    }

    // ========================================================================
    // 测试流 D：查询密码
    // ========================================================================

    @Nested
    @DisplayName("测试流 D：查询房间当前密码")
    class GetCurrentPasswordFlow {

        @Test
        @DisplayName("房主查询密码 → 返回解密后明文")
        void getCurrentPassword_AsOwner_ShouldReturnDecryptedPassword() {
            Long ownerId = uid();
            String rawPassword = "OwnerQueryPass_" + uid();
            RoomVO room = roomService.createRoom(
                    buildRoomDto("查询密码_" + uid(), RoomConst.PASSWORD_MODE_FIXED, rawPassword), ownerId);

            RoomPasswordVO result = roomService.getRoomCurrentPassword(room.getRoomId(), ownerId);

            assertThat(result).isNotNull();
            assertThat(result.getCurrentPassword()).isEqualTo(rawPassword);
        }

        @Test
        @DisplayName("普通成员在密码可见时查询 → 返回解密后明文")
        void getCurrentPassword_AsMember_Visible_ShouldReturnPassword() {
            Long ownerId = uid();
            Long memberId = uid();
            String rawPassword = "MemberQueryPass_" + uid();
            RoomVO room = roomService.createRoom(
                    buildRoomDto("成员查密_" + uid(), RoomConst.PASSWORD_MODE_FIXED, rawPassword), ownerId);

            addMemberWithCache(room.getRoomId(), memberId, RoomConst.ROLE_MEMBER);

            RoomPasswordVO result = roomService.getRoomCurrentPassword(room.getRoomId(), memberId);

            assertThat(result).isNotNull();
            assertThat(result.getCurrentPassword()).isEqualTo(rawPassword);
        }

        @Test
        @DisplayName("非成员查询密码 → 抛 NOT_MEMBER")
        void getCurrentPassword_AsNonMember_ShouldThrow() {
            Long ownerId = uid();
            Long strangerId = uid();
            RoomVO room = roomService.createRoom(
                    buildRoomDto("非成员查密_" + uid(), RoomConst.PASSWORD_MODE_FIXED, "TestPass"), ownerId);

            RoomException ex = assertThrows(RoomException.class,
                    () -> roomService.getRoomCurrentPassword(room.getRoomId(), strangerId));
            assertThat(ex.getErrorCode().getCode()).isEqualTo(RoomErrorCode.NOT_MEMBER.getCode());
        }

        @Test
        @DisplayName("成员查询密码（不可见）→ 抛 NOT_MEMBER")
        void getCurrentPassword_AsMember_Invisible_ShouldThrow() {
            Long ownerId = uid();
            Long memberId = uid();
            RoomVO room = roomService.createRoom(
                    buildRoomDto("不可见密码_" + uid(), RoomConst.PASSWORD_MODE_FIXED, "TestPass"), ownerId);
            addMemberWithCache(room.getRoomId(), memberId, RoomConst.ROLE_MEMBER);

            RoomException ex = assertThrows(RoomException.class,
                    () -> roomService.getRoomCurrentPassword(room.getRoomId(), memberId));
            assertThat(ex.getErrorCode().getCode()).isEqualTo(RoomErrorCode.NOT_MEMBER.getCode());
        }
    }

    // ========================================================================
    // 测试流 E：续期与重新开启
    // ========================================================================

    @Nested
    @DisplayName("测试流 E：续期与重新开启")
    class RenewAndReopenFlow {

        @Test
        @DisplayName("房主续期 ACTIVE 房间 → 过期时间延长")
        void renewRoom_Success_ActiveRoom() {
            Long ownerId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("续期测试_" + uid(), RoomConst.PASSWORD_MODE_NONE), ownerId);

            RoomVO renewed = roomService.renewRoom(room.getRoomId(), ownerId, 60);

            assertThat(renewed).isNotNull();
            assertThat(renewed.getStatus()).isEqualTo(RoomConst.STATUS_ACTIVE);
        }

        @Test
        @DisplayName("非房主续期 → 抛 NO_PERMISSION")
        void renewRoom_NonOwner_ShouldThrow() {
            Long ownerId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("续期权限_" + uid(), RoomConst.PASSWORD_MODE_NONE), ownerId);

            RoomException ex = assertThrows(RoomException.class,
                    () -> roomService.renewRoom(room.getRoomId(), uid(), 60));
            assertThat(ex.getErrorCode().getCode()).isEqualTo(RoomErrorCode.NO_PERMISSION.getCode());
        }

        @Test
        @DisplayName("重新开启已关闭房间 → ACTIVE")
        void reopenRoom_Success_ClosedRoom() {
            Long ownerId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("重新开启_" + uid(), RoomConst.PASSWORD_MODE_NONE), ownerId);
            roomService.closeRoom(room.getRoomId(), ownerId);

            RoomVO reopened = roomService.reopenRoom(room.getRoomId(), ownerId, 60);

            assertThat(reopened).isNotNull();
            assertThat(reopened.getStatus()).isEqualTo(RoomConst.STATUS_ACTIVE);
        }

        @Test
        @DisplayName("非房主重新开启 → 抛 NO_PERMISSION")
        void reopenRoom_NonOwner_ShouldThrow() {
            Long ownerId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("重开权限_" + uid(), RoomConst.PASSWORD_MODE_NONE), ownerId);

            RoomException ex = assertThrows(RoomException.class,
                    () -> roomService.reopenRoom(room.getRoomId(), uid(), 60));
            assertThat(ex.getErrorCode().getCode()).isEqualTo(RoomErrorCode.NO_PERMISSION.getCode());
        }
    }

    // ========================================================================
    // 测试流 F：异步加入
    // ========================================================================

    @Nested
    @DisplayName("测试流 F：异步加入房间")
    class JoinAsyncFlow {

        @Test
        @DisplayName("无密码房间申请加入 → 返回 joinToken")
        void joinRoomAsync_NoPassword_Success() {
            Long ownerId = uid();
            Long memberId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("异步入房_" + uid(), RoomConst.PASSWORD_MODE_NONE), ownerId);

            JoinRoomDto joinDto = new JoinRoomDto();
            joinDto.setRoomCode(room.getRoomCode());
            JoinAcceptedVO accepted = roomService.joinRoomAsync(joinDto, memberId);

            assertThat(accepted.getJoinToken()).isNotBlank();
        }

        @Test
        @DisplayName("TOTP 房间申请加入 → 返回 joinToken")
        void joinRoomAsync_TotpMode_Success() {
            Long ownerId = uid();
            Long memberId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("TOTP入房_" + uid(), RoomConst.PASSWORD_MODE_TOTP), ownerId);

            JoinRoomDto joinDto = new JoinRoomDto();
            joinDto.setRoomCode(room.getRoomCode());
            joinDto.setPassword(PasswordUtils.getCurrentTotp(
                    roomMapper.selectById(room.getRoomId()).getPasswordHash()));
            JoinAcceptedVO accepted = roomService.joinRoomAsync(joinDto, memberId);

            assertThat(accepted.getJoinToken()).isNotBlank();
        }

        @Test
        @DisplayName("用户已在房间中 → 抛 ALREADY_IN_ROOM")
        void joinRoomAsync_AlreadyInRoom_ShouldThrow() {
            Long ownerId = uid();
            RoomVO room = createRoomWithCache(buildRoomDto("重复入房_" + uid(), RoomConst.PASSWORD_MODE_NONE), ownerId);

            JoinRoomDto joinDto = new JoinRoomDto();
            joinDto.setRoomCode(room.getRoomCode());

            RoomException ex = assertThrows(RoomException.class,
                    () -> roomService.joinRoomAsync(joinDto, ownerId));
            assertThat(ex.getErrorCode().getCode()).isEqualTo(RoomErrorCode.ALREADY_IN_ROOM.getCode());
        }

        @Test
        @DisplayName("无效房间码 → 抛 ROOM_CODE_INVALID")
        void joinRoomAsync_InvalidRoomCode_ShouldThrow() {
            JoinRoomDto joinDto = new JoinRoomDto();
            joinDto.setRoomCode("00000000");

            RoomException ex = assertThrows(RoomException.class,
                    () -> roomService.joinRoomAsync(joinDto, uid()));
            assertThat(ex.getErrorCode().getCode()).isEqualTo(RoomErrorCode.ROOM_CODE_INVALID.getCode());
        }
    }

    // ========================================================================
    // 测试流 G：用户在线状态
    // ========================================================================

    @Nested
    @DisplayName("测试流 G：用户在线状态批量更新")
    class UserOnlineStatusFlow {

        @Test
        @DisplayName("批量更新用户为离线 → 多个房间成员状态变更")
        void updateStatusToOffline_ShouldUpdateAllRooms() {
            Long userId = uid();
            Long room1Owner = uid();
            Long room2Owner = uid();

            RoomVO room1 = createRoomWithCache(buildRoomDto("用户状态1_" + uid(), RoomConst.PASSWORD_MODE_NONE), room1Owner);
            RoomVO room2 = createRoomWithCache(buildRoomDto("用户状态2_" + uid(), RoomConst.PASSWORD_MODE_NONE), room2Owner);

            addMemberWithCache(room1.getRoomId(), userId, RoomConst.ROLE_MEMBER);
            addMemberWithCache(room2.getRoomId(), userId, RoomConst.ROLE_MEMBER);

            int updated = roomMemberService.updateStatusToOffline(userId);
            assertThat(updated).isGreaterThanOrEqualTo(2);

            List<RoomMember> members1 = roomMemberMapper.selectList(
                    new LambdaQueryWrapper<RoomMember>()
                            .eq(RoomMember::getRoomId, room1.getRoomId())
                            .eq(RoomMember::getUserId, userId));
            List<RoomMember> members2 = roomMemberMapper.selectList(
                    new LambdaQueryWrapper<RoomMember>()
                            .eq(RoomMember::getRoomId, room2.getRoomId())
                            .eq(RoomMember::getUserId, userId));

            assertThat(members1.get(0).getStatus()).isEqualTo(RoomConst.MEMBER_STATUS_OFFLINE);
            assertThat(members2.get(0).getStatus()).isEqualTo(RoomConst.MEMBER_STATUS_OFFLINE);
        }
    }

    // ========================================================================
    // 测试数据构建辅助方法
    // ========================================================================

    private RoomDto buildRoomDto(String roomName, int passwordMode) {
        return buildRoomDto(roomName, passwordMode, null);
    }

    private RoomDto buildRoomDto(String roomName, int passwordMode, String rawPassword) {
        RoomDto dto = new RoomDto();
        dto.setRoomName(roomName);
        dto.setMaxMembers(10);
        dto.setPasswordMode(passwordMode);
        if (rawPassword != null) {
            dto.setRawPassword(rawPassword);
            dto.setPasswordVisible(1);
        }
        return dto;
    }
}
