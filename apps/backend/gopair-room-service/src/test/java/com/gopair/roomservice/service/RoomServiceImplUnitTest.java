package com.gopair.roomservice.service;

import com.gopair.common.core.PageResult;
import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.domain.dto.JoinRoomDto;
import com.gopair.roomservice.domain.dto.RoomDto;
import com.gopair.roomservice.domain.dto.RoomQueryDto;
import com.gopair.roomservice.domain.dto.RoomQueryDto;
import com.gopair.roomservice.domain.event.LeaveRoomRequestedEvent;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.domain.po.RoomMember;
import com.gopair.roomservice.domain.vo.JoinAcceptedVO;
import com.gopair.roomservice.domain.vo.RoomVO;
import com.gopair.roomservice.exception.RoomException;
import com.gopair.roomservice.enums.RoomErrorCode;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import com.gopair.roomservice.messaging.LeaveRoomProducer;
import com.gopair.roomservice.service.impl.JoinReservationServiceImpl;
import com.gopair.roomservice.service.impl.JoinResultQueryServiceImpl;
import com.gopair.roomservice.service.impl.RoomCacheSyncServiceImpl;
import com.gopair.roomservice.service.impl.RoomMemberServiceImpl;
import com.gopair.roomservice.service.impl.RoomServiceImpl;
import com.gopair.roomservice.util.PasswordUtils;
import com.gopair.roomservice.config.RoomConfig;
import com.gopair.common.service.WebSocketMessageProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 房间服务核心逻辑单元测试。
 *
 * * [核心策略]
 * - 隔离性：使用 @ExtendWith(MockitoExtension.class)，外部依赖全部 @Mock。
 * - 复杂私有方法：通过反射调用 verifyRoomPassword（密码校验）和 enhanceRoomsWithUserRelationship（关系增强），
 *   覆盖三种密码模式和无 DB 降级场景。
 * - afterCommit 回调：在单元测试中无法触发，仅验证返回值和异常抛出，不验证 Redis/WebSocket 副作用。
 *
 * * [测试范围]
 * - verifyRoomPassword：三种密码模式（NONE/FIXED/TOTP）的校验路径
 * - joinRoomAsync：满员、已关闭、已过期、密码错误等分支
 * - enhanceRoomsWithUserRelationship：IN 查询降级和关系判断
 * - 委托方法：getUserRooms / isMemberInRoom / getRoomByCode / findExpiredRooms / isRoomCodeUnique
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceImplUnitTest {

    @Mock
    private RoomMapper roomMapper;

    @Mock
    private RoomMemberMapper roomMemberMapper;

    @Mock
    private RoomMemberService roomMemberService;

    @Mock
    private JoinReservationServiceImpl joinReservationService;

    @Mock
    private JoinResultQueryServiceImpl joinResultQueryService;

    @Mock
    private RoomCacheSyncServiceImpl roomCacheSyncService;

    @Mock
    private LeaveRoomProducer leaveRoomProducer;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private WebSocketMessageProducer wsProducer;

    @Mock
    private RoomConfig roomConfig;

    @Mock
    private RoomConfig.Password passwordConfig;

    private RoomServiceImpl roomService;

    private static final Long OWNER_ID = 1L;
    private static final Long USER_ID_2 = 2L;
    private static final String MASTER_KEY = "test-master-key-for-testing";

    @BeforeEach
    void setUp() throws Exception {
        // 配置密码
        lenient().when(roomConfig.getPassword()).thenReturn(passwordConfig);
        lenient().when(passwordConfig.getMasterKey()).thenReturn(MASTER_KEY);
        lenient().when(roomConfig.getDefaultExpireHours()).thenReturn(24);

        roomService = new RoomServiceImpl(
                roomMapper,
                roomMemberMapper,
                roomMemberService,
                joinReservationService,
                joinResultQueryService,
                roomCacheSyncService,
                leaveRoomProducer,
                stringRedisTemplate,
                wsProducer,
                roomConfig
        );
    }

    // ==================== verifyRoomPassword 测试 ====================

    @Nested
    @DisplayName("verifyRoomPassword 三种密码模式")
    class VerifyPasswordTests {

        @Test
        @DisplayName("NONE 模式：空密码直接通过")
        void noneMode_WithNullPassword_ShouldPass() throws Exception {
            Method method = RoomServiceImpl.class.getDeclaredMethod(
                    "verifyRoomPassword", Room.class, String.class);
            method.setAccessible(true);

            Room room = new Room();
            room.setRoomId(1L);
            room.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
            room.setPasswordHash(null);

            // NONE 模式无异常即通过
            method.invoke(roomService, room, null);
            method.invoke(roomService, room, "");
            method.invoke(roomService, room, "any-password");
        }

        @Test
        @DisplayName("FIXED 模式：正确密码通过，错误密码抛异常")
        void fixedMode_WithCorrectPassword_ShouldPass() throws Exception {
            Method method = RoomServiceImpl.class.getDeclaredMethod(
                    "verifyRoomPassword", Room.class, String.class);
            method.setAccessible(true);

            String rawPassword = "test1234";
            String encrypted = PasswordUtils.encryptPassword(rawPassword, 1L, MASTER_KEY);

            Room room = new Room();
            room.setRoomId(1L);
            room.setPasswordMode(RoomConst.PASSWORD_MODE_FIXED);
            room.setPasswordHash(encrypted);

            // 正确密码不抛异常
            method.invoke(roomService, room, rawPassword);
        }

        @Test
        @DisplayName("FIXED 模式：空密码抛异常")
        void fixedMode_WithNullPassword_ShouldThrow() throws Exception {
            Method method = RoomServiceImpl.class.getDeclaredMethod(
                    "verifyRoomPassword", Room.class, String.class);
            method.setAccessible(true);

            Room room = new Room();
            room.setRoomId(1L);
            room.setPasswordMode(RoomConst.PASSWORD_MODE_FIXED);
            room.setPasswordHash("somehash");

            assertThrows(Exception.class, () -> method.invoke(roomService, room, (String) null));
            assertThrows(Exception.class, () -> method.invoke(roomService, room, ""));
        }

        @Test
        @DisplayName("FIXED 模式：错误密码抛异常")
        void fixedMode_WithWrongPassword_ShouldThrow() throws Exception {
            Method method = RoomServiceImpl.class.getDeclaredMethod(
                    "verifyRoomPassword", Room.class, String.class);
            method.setAccessible(true);

            String encrypted = PasswordUtils.encryptPassword("correct", 1L, MASTER_KEY);

            Room room = new Room();
            room.setRoomId(1L);
            room.setPasswordMode(RoomConst.PASSWORD_MODE_FIXED);
            room.setPasswordHash(encrypted);

            assertThrows(Exception.class, () -> method.invoke(roomService, room, "wrongpassword"));
        }

        @Test
        @DisplayName("TOTP 模式：正确令牌通过，错误令牌抛异常")
        void totpMode_WithCorrectTotp_ShouldPass() throws Exception {
            Method method = RoomServiceImpl.class.getDeclaredMethod(
                    "verifyRoomPassword", Room.class, String.class);
            method.setAccessible(true);

            String secret = PasswordUtils.generateTotpSecret();
            String validCode = PasswordUtils.getCurrentTotp(secret);

            Room room = new Room();
            room.setRoomId(1L);
            room.setPasswordMode(RoomConst.PASSWORD_MODE_TOTP);
            room.setPasswordHash(secret);

            // 正确 TOTP 不抛异常
            method.invoke(roomService, room, validCode);
        }

        @Test
        @DisplayName("TOTP 模式：错误令牌抛异常")
        void totpMode_WithWrongTotp_ShouldThrow() throws Exception {
            Method method = RoomServiceImpl.class.getDeclaredMethod(
                    "verifyRoomPassword", Room.class, String.class);
            method.setAccessible(true);

            String secret = PasswordUtils.generateTotpSecret();

            Room room = new Room();
            room.setRoomId(1L);
            room.setPasswordMode(RoomConst.PASSWORD_MODE_TOTP);
            room.setPasswordHash(secret);

            assertThrows(Exception.class, () -> method.invoke(roomService, room, "000000"));
        }
    }

    // ==================== joinRoomAsync 测试 ====================

    @Nested
    @DisplayName("joinRoomAsync 分支逻辑")
    class JoinRoomAsyncTests {

        @Test
        @DisplayName("空房间码抛异常")
        void nullRoomCode_ShouldThrow() {
            JoinRoomDto dto = new JoinRoomDto();
            dto.setRoomCode(null);

            assertThrows(Exception.class, () -> roomService.joinRoomAsync(dto, OWNER_ID));
        }

        @Test
        @DisplayName("空 userId 抛异常")
        void nullUserId_ShouldThrow() {
            JoinRoomDto dto = new JoinRoomDto();
            dto.setRoomCode("12345678");

            assertThrows(Exception.class, () -> roomService.joinRoomAsync(dto, null));
        }

        @Test
        @DisplayName("房间不存在抛异常")
        void roomNotFound_ShouldThrow() {
            when(roomMapper.selectByRoomCode("12345678")).thenReturn(null);

            JoinRoomDto dto = new JoinRoomDto();
            dto.setRoomCode("12345678");

            assertThrows(Exception.class, () -> roomService.joinRoomAsync(dto, USER_ID_2));
        }

        @Test
        @DisplayName("密码模式时未传密码抛异常")
        void fixedPasswordMode_WithNullPassword_ShouldThrow() {
            Room room = new Room();
            room.setRoomId(1L);
            room.setPasswordMode(RoomConst.PASSWORD_MODE_FIXED);
            room.setPasswordHash(PasswordUtils.encryptPassword("correct", 1L, MASTER_KEY));
            when(roomMapper.selectByRoomCode("12345678")).thenReturn(room);

            JoinRoomDto dto = new JoinRoomDto();
            dto.setRoomCode("12345678");
            dto.setPassword(null);

            assertThrows(Exception.class, () -> roomService.joinRoomAsync(dto, USER_ID_2));
        }

        @Test
        @DisplayName("ALREADY_JOINED 返回直接提示")
        void alreadyJoined_ShouldReturnDirectMessage() {
            Room room = new Room();
            room.setRoomId(1L);
            room.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
            when(roomMapper.selectByRoomCode("12345678")).thenReturn(room);

            when(joinReservationService.preReserve(eq(1L), eq(USER_ID_2)))
                    .thenReturn(JoinReservationServiceImpl.PreReserveResult.of(
                            JoinReservationServiceImpl.ReserveStatus.ALREADY_JOINED, null, "已在房间"));

            JoinRoomDto dto = new JoinRoomDto();
            dto.setRoomCode("12345678");

            JoinAcceptedVO result = roomService.joinRoomAsync(dto, USER_ID_2);

            assertNotNull(result);
            assertNull(result.getJoinToken());
            assertEquals("已在房间", result.getMessage());
        }

        @Test
        @DisplayName("FULL 抛 RoomFullException")
        void full_ShouldThrowRoomFull() {
            Room room = new Room();
            room.setRoomId(1L);
            room.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
            when(roomMapper.selectByRoomCode("12345678")).thenReturn(room);

            when(joinReservationService.preReserve(eq(1L), eq(USER_ID_2)))
                    .thenReturn(JoinReservationServiceImpl.PreReserveResult.of(
                            JoinReservationServiceImpl.ReserveStatus.FULL, null, "FULL"));

            JoinRoomDto dto = new JoinRoomDto();
            dto.setRoomCode("12345678");

            assertThrows(Exception.class, () -> roomService.joinRoomAsync(dto, USER_ID_2));
        }

        @Test
        @DisplayName("CLOSED 抛 RoomClosedException")
        void closed_ShouldThrowRoomClosed() {
            Room room = new Room();
            room.setRoomId(1L);
            room.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
            when(roomMapper.selectByRoomCode("12345678")).thenReturn(room);

            when(joinReservationService.preReserve(eq(1L), eq(USER_ID_2)))
                    .thenReturn(JoinReservationServiceImpl.PreReserveResult.of(
                            JoinReservationServiceImpl.ReserveStatus.CLOSED, null, "CLOSED"));

            JoinRoomDto dto = new JoinRoomDto();
            dto.setRoomCode("12345678");

            assertThrows(Exception.class, () -> roomService.joinRoomAsync(dto, USER_ID_2));
        }

        @Test
        @DisplayName("EXPIRED 抛 RoomExpiredException")
        void expired_ShouldThrowRoomExpired() {
            Room room = new Room();
            room.setRoomId(1L);
            room.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
            when(roomMapper.selectByRoomCode("12345678")).thenReturn(room);

            when(joinReservationService.preReserve(eq(1L), eq(USER_ID_2)))
                    .thenReturn(JoinReservationServiceImpl.PreReserveResult.of(
                            JoinReservationServiceImpl.ReserveStatus.EXPIRED, null, "EXPIRED"));

            JoinRoomDto dto = new JoinRoomDto();
            dto.setRoomCode("12345678");

            assertThrows(Exception.class, () -> roomService.joinRoomAsync(dto, USER_ID_2));
        }

        @Test
        @DisplayName("ACCEPTED 返回 joinToken")
        void accepted_ShouldReturnJoinToken() {
            Room room = new Room();
            room.setRoomId(1L);
            room.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
            when(roomMapper.selectByRoomCode("12345678")).thenReturn(room);

            String token = "abc123token";
            when(joinReservationService.preReserve(eq(1L), eq(USER_ID_2)))
                    .thenReturn(JoinReservationServiceImpl.PreReserveResult.of(
                            JoinReservationServiceImpl.ReserveStatus.ACCEPTED, token, "已受理"));

            JoinRoomDto dto = new JoinRoomDto();
            dto.setRoomCode("12345678");

            JoinAcceptedVO result = roomService.joinRoomAsync(dto, USER_ID_2);

            assertNotNull(result);
            assertEquals(token, result.getJoinToken());
            assertEquals("已受理", result.getMessage());
        }
    }

    // ==================== enhanceRoomsWithUserRelationship 测试 ====================

    @Nested
    @DisplayName("enhanceRoomsWithUserRelationship 关系增强")
    class EnhanceRoomsTests {

        @Test
        @DisplayName("空列表直接返回")
        void emptyList_ShouldReturnImmediately() throws Exception {
            Method method = RoomServiceImpl.class.getDeclaredMethod(
                    "enhanceRoomsWithUserRelationship", List.class, Long.class);
            method.setAccessible(true);

            // 空列表不抛异常
            method.invoke(roomService, List.of(), OWNER_ID);
            method.invoke(roomService, (List) null, OWNER_ID);
        }

        @Test
        @DisplayName("房主关系判断：ownerId 匹配时 relationshipType=created")
        void ownerRelationship_ShouldMarkAsCreated() throws Exception {
            RoomVO room = new RoomVO();
            room.setRoomId(1L);
            room.setOwnerId(OWNER_ID);
            room.setCreateTime(LocalDateTime.now());

            RoomMember ownerMember = new RoomMember();
            ownerMember.setRoomId(1L);
            ownerMember.setUserId(OWNER_ID);
            ownerMember.setRole(RoomConst.ROLE_OWNER);
            ownerMember.setJoinTime(LocalDateTime.now());

            when(roomMemberMapper.selectList(any()))
                    .thenReturn(List.of(ownerMember));

            Method method = RoomServiceImpl.class.getDeclaredMethod(
                    "enhanceRoomsWithUserRelationship", List.class, Long.class);
            method.setAccessible(true);

            method.invoke(roomService, List.of(room), OWNER_ID);

            assertEquals("created", room.getRelationshipType());
            assertEquals(RoomConst.ROLE_OWNER, room.getUserRole());
        }

        @Test
        @DisplayName("普通成员关系判断：relationshipType=joined")
        void memberRelationship_ShouldMarkAsJoined() throws Exception {
            Long normalUserId = 999L;

            RoomVO room = new RoomVO();
            room.setRoomId(1L);
            room.setOwnerId(OWNER_ID);
            room.setCreateTime(LocalDateTime.now());

            RoomMember member = new RoomMember();
            member.setRoomId(1L);
            member.setUserId(normalUserId);
            member.setRole(RoomConst.ROLE_MEMBER);
            member.setJoinTime(LocalDateTime.now());

            when(roomMemberMapper.selectList(any()))
                    .thenReturn(List.of(member));

            Method method = RoomServiceImpl.class.getDeclaredMethod(
                    "enhanceRoomsWithUserRelationship", List.class, Long.class);
            method.setAccessible(true);

            method.invoke(roomService, List.of(room), normalUserId);

            assertEquals("joined", room.getRelationshipType());
            assertEquals(RoomConst.ROLE_MEMBER, room.getUserRole());
        }

        @Test
        @DisplayName("成员信息缺失时降级为 joined")
        void missingMember_ShouldFallbackToJoined() throws Exception {
            Long orphanUserId = 888L;

            RoomVO room = new RoomVO();
            room.setRoomId(1L);
            room.setOwnerId(OWNER_ID); // 不是房主
            room.setCreateTime(LocalDateTime.now());

            // 查询结果为空（降级场景）
            when(roomMemberMapper.selectList(any()))
                    .thenReturn(List.of());

            Method method = RoomServiceImpl.class.getDeclaredMethod(
                    "enhanceRoomsWithUserRelationship", List.class, Long.class);
            method.setAccessible(true);

            method.invoke(roomService, List.of(room), orphanUserId);

            // 降级为普通成员
            assertEquals("joined", room.getRelationshipType());
            assertEquals(RoomConst.ROLE_MEMBER, room.getUserRole());
        }
    }

    // ==================== leaveRoom 权限与流程测试 ====================

    @Nested
    @DisplayName("leaveRoom 权限与流程")
    class LeaveRoomTests {

        @Test
        @DisplayName("空 userId 抛异常")
        void nullUserId_ShouldThrow() {
            assertThrows(Exception.class, () -> roomService.leaveRoom(1L, null));
        }

        @Test
        @DisplayName("用户不在房间内抛异常")
        void notInRoom_ShouldThrow() {
            when(roomMemberService.isMemberInRoom(1L, USER_ID_2)).thenReturn(false);

            assertThrows(Exception.class, () -> roomService.leaveRoom(1L, USER_ID_2));
        }
    }

    // ==================== closeRoom 权限测试 ====================

    @Nested
    @DisplayName("closeRoom 权限与流程")
    class CloseRoomTests {

        @Test
        @DisplayName("非房主关闭抛异常")
        void nonOwner_ShouldThrowNoPermission() {
            Room room = new Room();
            room.setRoomId(1L);
            room.setOwnerId(OWNER_ID);
            when(roomMapper.selectById(1L)).thenReturn(room);

            assertThrows(Exception.class, () -> roomService.closeRoom(1L, USER_ID_2));
        }

        @Test
        @DisplayName("房间不存在抛异常")
        void roomNotFound_ShouldThrow() {
            when(roomMapper.selectById(1L)).thenReturn(null);

            assertThrows(Exception.class, () -> roomService.closeRoom(1L, OWNER_ID));
        }
    }

    // ==================== kickMember 权限测试 ====================

    @Nested
    @DisplayName("kickMember 权限与流程")
    class KickMemberTests {

        @Test
        @DisplayName("非房主踢人抛异常")
        void nonOwner_ShouldThrowNoPermission() {
            Room room = new Room();
            room.setRoomId(1L);
            room.setOwnerId(OWNER_ID);
            when(roomMapper.selectById(1L)).thenReturn(room);

            assertThrows(Exception.class, () -> roomService.kickMember(1L, USER_ID_2, 3L));
        }

        @Test
        @DisplayName("房主自踢抛异常")
        void ownerKickSelf_ShouldThrowNoPermission() {
            Room room = new Room();
            room.setRoomId(1L);
            room.setOwnerId(OWNER_ID);
            when(roomMapper.selectById(1L)).thenReturn(room);

            assertThrows(Exception.class, () -> roomService.kickMember(1L, OWNER_ID, OWNER_ID));
        }

        @Test
        @DisplayName("目标用户不在房间抛异常")
        void targetNotInRoom_ShouldThrow() {
            Room room = new Room();
            room.setRoomId(1L);
            room.setOwnerId(OWNER_ID);
            when(roomMapper.selectById(1L)).thenReturn(room);

            assertThrows(Exception.class, () -> roomService.kickMember(1L, OWNER_ID, USER_ID_2));
        }
    }

    // ==================== updateRoomPassword 权限测试 ====================

    @Nested
    @DisplayName("updateRoomPassword 权限与流程")
    class UpdatePasswordTests {

        @Test
        @DisplayName("非房主修改密码抛异常")
        void nonOwner_ShouldThrowNoPermission() {
            Room room = new Room();
            room.setRoomId(1L);
            room.setOwnerId(OWNER_ID);
            when(roomMapper.selectById(1L)).thenReturn(room);

            assertThrows(Exception.class,
                    () -> roomService.updateRoomPassword(1L, USER_ID_2, RoomConst.PASSWORD_MODE_NONE, null, 1));
        }

        @Test
        @DisplayName("FIXED 模式未传密码抛异常")
        void fixedModeWithoutPassword_ShouldThrow() {
            Room room = new Room();
            room.setRoomId(1L);
            room.setOwnerId(OWNER_ID);
            when(roomMapper.selectById(1L)).thenReturn(room);

            assertThrows(Exception.class,
                    () -> roomService.updateRoomPassword(1L, OWNER_ID, RoomConst.PASSWORD_MODE_FIXED, null, 1));
        }
    }

    // ==================== getRoomCurrentPassword 权限测试 ====================

    @Nested
    @DisplayName("getRoomCurrentPassword 权限与流程")
    class GetCurrentPasswordTests {

        @Test
        @DisplayName("非房主且非成员无权查看")
        void nonMember_ShouldThrowNoPermission() {
            Room room = new Room();
            room.setRoomId(1L);
            room.setOwnerId(OWNER_ID);
            room.setPasswordMode(RoomConst.PASSWORD_MODE_FIXED);
            room.setPasswordVisible(1);
            when(roomMapper.selectById(1L)).thenReturn(room);
            when(roomMemberService.isMemberInRoom(1L, USER_ID_2)).thenReturn(false);

            assertThrows(Exception.class,
                    () -> roomService.getRoomCurrentPassword(1L, USER_ID_2));
        }
    }

    // ==================== 委托方法测试（覆盖原 RoomServiceTest 场景） ====================

    @Nested
    @DisplayName("委托方法覆盖")
    class DelegateMethodTests {

        @Test
        @DisplayName("getUserRooms 委托 roomMemberService，返回分页结果")
        void getUserRooms_ShouldDelegate() {
            RoomQueryDto query = new RoomQueryDto();
            query.setPageNum(1);
            query.setPageSize(10);

            RoomVO room = new RoomVO();
            room.setRoomId(1L);
            room.setOwnerId(OWNER_ID);
            room.setCreateTime(LocalDateTime.now());

            PageResult<RoomVO> expected = new PageResult<>(List.of(room), 1L, 1L, 10L);
            when(roomMemberService.getUserRooms(eq(OWNER_ID), any(RoomQueryDto.class)))
                    .thenReturn(expected);

            PageResult<RoomVO> result = roomService.getUserRooms(OWNER_ID, query);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
            assertEquals(1, result.getRecords().size());
        }

        @Test
        @DisplayName("getUserRooms 空结果返回空分页")
        void getUserRooms_EmptyResult_ShouldReturnEmptyPage() {
            RoomQueryDto query = new RoomQueryDto();
            PageResult<RoomVO> emptyResult = new PageResult<>(List.of(), 0L, 1L, 10L);
            when(roomMemberService.getUserRooms(eq(OWNER_ID), any(RoomQueryDto.class)))
                    .thenReturn(emptyResult);

            PageResult<RoomVO> result = roomService.getUserRooms(OWNER_ID, query);

            assertNotNull(result);
            assertEquals(0, result.getTotal());
            assertTrue(result.getRecords().isEmpty());
        }

        @Test
        @DisplayName("isMemberInRoom 委托 roomMemberService")
        void isMemberInRoom_ShouldDelegate() {
            when(roomMemberService.isMemberInRoom(1L, USER_ID_2)).thenReturn(true);

            assertTrue(roomService.isMemberInRoom(1L, USER_ID_2));
            verify(roomMemberService).isMemberInRoom(1L, USER_ID_2);
        }

        @Test
        @DisplayName("isMemberInRoom null 参数返回 false")
        void isMemberInRoom_NullParams_ShouldReturnFalse() {
            assertFalse(roomService.isMemberInRoom(null, 100L));
            assertFalse(roomService.isMemberInRoom(1L, null));
        }

        @Test
        @DisplayName("getRoomByCode 存在时返回 RoomVO")
        void getRoomByCode_Exists_ShouldReturnRoomVO() {
            Room room = new Room();
            room.setRoomId(1L);
            room.setRoomName("测试房间");
            room.setRoomCode("12345678");
            room.setCreateTime(LocalDateTime.now());
            when(roomMapper.selectByRoomCode("12345678")).thenReturn(room);

            RoomVO result = roomService.getRoomByCode("12345678");

            assertNotNull(result);
            assertEquals(1L, result.getRoomId());
            assertEquals("测试房间", result.getRoomName());
        }

        @Test
        @DisplayName("getRoomByCode 不存在时抛出 ROOM_NOT_FOUND 异常")
        void getRoomByCode_NotExists_ShouldThrow() {
            when(roomMapper.selectByRoomCode("00000000")).thenReturn(null);

            RoomException ex = assertThrows(RoomException.class,
                    () -> roomService.getRoomByCode("00000000"));

            assertEquals(RoomErrorCode.ROOM_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("findExpiredRooms 委托 roomMapper")
        void findExpiredRooms_ShouldDelegate() {
            Room expiredRoom = new Room();
            expiredRoom.setRoomId(1L);
            expiredRoom.setRoomName("过期房间");
            when(roomMapper.selectExpiredRooms(any(LocalDateTime.class)))
                    .thenReturn(List.of(expiredRoom));

            List<Room> result = roomService.findExpiredRooms();

            assertNotNull(result);
            assertEquals(1, result.size());
            verify(roomMapper).selectExpiredRooms(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("isRoomCodeUnique 码存在返回 false")
        void isRoomCodeUnique_Exists_ShouldReturnFalse() {
            Room existingRoom = new Room();
            existingRoom.setRoomId(1L);
            when(roomMapper.selectByRoomCode("12345678")).thenReturn(existingRoom);

            assertFalse(roomService.isRoomCodeUnique("12345678"));
        }

        @Test
        @DisplayName("isRoomCodeUnique 码不存在返回 true")
        void isRoomCodeUnique_NotExists_ShouldReturnTrue() {
            when(roomMapper.selectByRoomCode("XYZ99999")).thenReturn(null);

            assertTrue(roomService.isRoomCodeUnique("XYZ99999"));
        }
    }
}
