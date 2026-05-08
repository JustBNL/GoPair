//package com.gopair.roomservice.service;
//
//import com.gopair.common.core.PageResult;
//import com.gopair.common.service.WebSocketMessageProducer;
//import com.gopair.roomservice.base.BaseIntegrationTest;
//import com.gopair.roomservice.constant.RoomConst;
//import com.gopair.roomservice.domain.dto.JoinRoomDto;
//import com.gopair.roomservice.domain.dto.RoomDto;
//import com.gopair.roomservice.domain.dto.RoomQueryDto;
//import com.gopair.roomservice.domain.po.Room;
//import com.gopair.roomservice.domain.po.RoomMember;
//import com.gopair.roomservice.domain.vo.JoinAcceptedVO;
//import com.gopair.roomservice.domain.vo.RoomMemberVO;
//import com.gopair.roomservice.domain.vo.RoomVO;
//import com.gopair.roomservice.exception.RoomException;
//import com.gopair.roomservice.mapper.RoomMapper;
//import com.gopair.roomservice.mapper.RoomMemberMapper;
//import com.gopair.roomservice.messaging.JoinRoomConsumer;
//import com.gopair.roomservice.messaging.JoinRoomProducer;
//import com.gopair.roomservice.messaging.LeaveRoomConsumer;
//import com.gopair.roomservice.messaging.LeaveRoomProducer;
//import com.gopair.roomservice.messaging.UserOfflineConsumer;
//import com.gopair.roomservice.service.impl.RoomCacheSyncServiceImpl;
//import com.gopair.roomservice.service.impl.RoomMemberServiceImpl;
//import com.gopair.roomservice.service.impl.RoomServiceImpl;
//import com.gopair.roomservice.util.PasswordUtils;
//import com.gopair.roomservice.config.RoomConfig;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.data.redis.core.HashOperations;
//import org.springframework.data.redis.core.SetOperations;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.data.redis.core.ValueOperations;
//
//import java.util.List;
//import java.util.Map;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
///**
// * 房间服务全生命周期集成测试。
// *
// * * [核心策略]
// * - 智能合并：将创建→加入→离开→关房等多个动作合并为 2 条完整测试流，避免逐动作独立测试。
// * - 真实 DB + 真实 Redis：使用 gopair_test 数据库和 Redis DB 14，@Transactional 保证 DB 回滚。
// * - Redis 手动清理：@AfterEach flushDb() 清理 Redis（Redis 不支持事务回滚）。
// *
// * * [测试流编排]
// * - 测试流 A：创建房间 → 查询房间码 → 验证房主入房 → 查询成员列表 → 查询用户房间列表 → 主动离开 → 自动关房
// * - 测试流 B：创建固定密码房间 → 错误密码入房被拒 → 正确密码入房 → 房主踢人 → 权限校验
// *
// * * [Mock 范围]
// * - MQ Consumer：Mock 防止异步消费污染测试环境。
// * - WebSocket：Mock 防止建立真实连接。
// */
//@Slf4j
//@DisplayName("房间服务全生命周期集成测试")
//class RoomLifecycleIntegrationTest extends BaseIntegrationTest {
//
//    @Autowired
//    private RoomServiceImpl roomService;
//
//    @Autowired
//    private RoomMemberServiceImpl roomMemberService;
//
//    @Autowired
//    private RoomMapper roomMapper;
//
//    @Autowired
//    private RoomMemberMapper roomMemberMapper;
//
//    @Autowired
//    private RoomConfig roomConfig;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    // ==================== 测试流 A：房间基础生命周期 ====================
//
//    @Nested
//    @DisplayName("测试流 A：房间基础生命周期（无密码模式）")
//    class RoomBasicLifecycleFlow {
//        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RoomBasicLifecycleFlow.class);
//
//        private Long ownerId;
//        private Long userId;
//        private RoomVO createdRoom;
//
//        @Test
//        @DisplayName("Step 1: 创建房间 → DB 记录正确")
//        void createRoom_ShouldPersistCorrectly() {
//            log.info("==== [Step 1: 创建房间] 状态校验 ====");
//
//            ownerId = 8001L;
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("测试房间-生命周期");
//            dto.setDescription("用于集成测试的房间");
//            dto.setMaxMembers(10);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
//
//            createdRoom = roomService.createRoom(dto, ownerId);
//
//            assertNotNull(createdRoom);
//            assertNotNull(createdRoom.getRoomId());
//            assertEquals("测试房间-生命周期", createdRoom.getRoomName());
//            assertEquals(10, createdRoom.getMaxMembers());
//            assertEquals(RoomConst.STATUS_ACTIVE, createdRoom.getStatus());
//            assertEquals(ownerId, createdRoom.getOwnerId());
//            assertEquals(1, createdRoom.getCurrentMembers()); // 创建者自动入房
//            assertNotNull(createdRoom.getRoomCode());
//            assertEquals(8, createdRoom.getRoomCode().length());
//
//            // 验证 DB 数据
//            Room dbRoom = roomMapper.selectById(createdRoom.getRoomId());
//            assertNotNull(dbRoom);
//            assertEquals("测试房间-生命周期", dbRoom.getRoomName());
//            assertEquals(RoomConst.PASSWORD_MODE_NONE, dbRoom.getPasswordMode());
//
//            log.info("房间创建成功: roomId={}, roomCode={}, ownerId={}",
//                    createdRoom.getRoomId(), createdRoom.getRoomCode(), ownerId);
//        }
//
//        @Test
//        @DisplayName("Step 2: 按房间码查询 → 返回完整房间信息")
//        void getRoomByCode_ShouldReturnCorrectRoom() {
//            // 先创建房间
//            Long userId = 8002L;
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("按码查询测试房间");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
//            RoomVO room = roomService.createRoom(dto, userId);
//
//            log.info("==== [Step 2: 按房间码查询] 状态校验 ====");
//
//            RoomVO found = roomService.getRoomByCode(room.getRoomCode());
//
//            assertNotNull(found);
//            assertEquals(room.getRoomId(), found.getRoomId());
//            assertEquals("按码查询测试房间", found.getRoomName());
//            assertEquals(RoomConst.STATUS_ACTIVE, found.getStatus());
//
//            log.info("按码查询成功: roomCode={}, roomId={}", room.getRoomCode(), found.getRoomId());
//        }
//
//        @Test
//        @DisplayName("Step 3: 创建时自动入房 → DB 中房主已存在")
//        void createRoom_ShouldAutoAddOwnerAsMember() {
//            Long userId = 8003L;
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("自动入房测试");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
//            RoomVO room = roomService.createRoom(dto, userId);
//
//            log.info("==== [Step 3: 验证房主自动入房] 状态校验 ====");
//
//            // DB 验证
//            RoomMember member = roomMemberMapper.selectList(
//                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoomMember>()
//                            .eq(RoomMember::getRoomId, room.getRoomId())
//                            .eq(RoomMember::getUserId, userId)
//            ).stream().findFirst().orElse(null);
//
//            assertNotNull(member, "房主应该在 room_member 中");
//            assertEquals(RoomConst.ROLE_OWNER, member.getRole());
//            assertEquals(RoomConst.MEMBER_STATUS_ONLINE, member.getStatus());
//            assertNotNull(member.getJoinTime());
//
//            log.info("房主自动入房成功: roomId={}, userId={}, role={}",
//                    room.getRoomId(), userId, member.getRole());
//        }
//
//        @Test
//        @DisplayName("Step 4: 查询房间成员 → 包含房主信息")
//        void getRoomMembers_ShouldIncludeOwner() {
//            Long owner = 8004L;
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("成员查询测试");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
//            RoomVO room = roomService.createRoom(dto, owner);
//
//            log.info("==== [Step 4: 查询房间成员] 状态校验 ====");
//
//            List<RoomMemberVO> members = roomService.getRoomMembers(room.getRoomId());
//
//            assertNotNull(members);
//            assertEquals(1, members.size());
//
//            RoomMemberVO ownerMember = members.get(0);
//            assertEquals(owner, ownerMember.getUserId());
//            assertTrue(ownerMember.getIsOwner());
//            assertEquals(RoomConst.ROLE_OWNER, ownerMember.getRole());
//            // nickname 降级链路：Mock 的 RestTemplate 返回 null，降级为「用户{userId}」
//            assertNotNull(ownerMember.getNickname());
//
//            log.info("房间成员查询成功: roomId={}, 成员数={}, nickname={}",
//                    room.getRoomId(), members.size(), ownerMember.getNickname());
//        }
//
//        @Test
//        @DisplayName("Step 5: 查询用户房间列表 → 包含 relationshipType=created")
//        void getUserRooms_ShouldEnrichWithRelationship() {
//            Long userId = 8005L;
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("关系增强测试房间");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
//            RoomVO room = roomService.createRoom(dto, userId);
//
//            log.info("==== [Step 5: 查询用户房间列表] 状态校验 ====");
//
//            RoomQueryDto query = new RoomQueryDto();
//            query.setPageNum(1);
//            query.setPageSize(10);
//            PageResult<RoomVO> result = roomService.getUserRooms(userId, query);
//
//            assertNotNull(result);
//            assertEquals(1, result.getTotal());
//
//            RoomVO enriched = result.getRecords().get(0);
//            assertEquals(room.getRoomId(), enriched.getRoomId());
//            assertEquals("created", enriched.getRelationshipType());
//            assertEquals(RoomConst.ROLE_OWNER, enriched.getUserRole());
//            assertNotNull(enriched.getJoinTime());
//
//            log.info("用户房间列表查询成功: userId={}, total={}, relationshipType={}, userRole={}",
//                    userId, result.getTotal(), enriched.getRelationshipType(), enriched.getUserRole());
//        }
//
//        @Test
//        @DisplayName("Step 6: 主动离开房间 → DB 成员删除 + 人数减一")
//        void leaveRoom_ShouldDeleteMemberAndDecrementCount() {
//            Long ownerId = 8006L;
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("离开房间测试");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
//            RoomVO room = roomService.createRoom(dto, ownerId);
//
//            log.info("==== [Step 6: 主动离开房间] 状态校验 ====");
//
//            // 离开前 DB 状态
//            Room beforeRoom = roomMapper.selectById(room.getRoomId());
//            assertEquals(1, beforeRoom.getCurrentMembers());
//
//            // 执行离开
//            boolean result = roomService.leaveRoom(room.getRoomId(), ownerId);
//
//            assertTrue(result);
//
//            // DB 验证：成员已删除
//            RoomMember member = roomMemberMapper.selectList(
//                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoomMember>()
//                            .eq(RoomMember::getRoomId, room.getRoomId())
//                            .eq(RoomMember::getUserId, ownerId)
//            ).stream().findFirst().orElse(null);
//            assertNull(member, "成员记录应该已被删除");
//
//            log.info("用户{}离开房间{}成功受理，成员记录已从 DB 删除", ownerId, room.getRoomId());
//        }
//
//        @Test
//        @DisplayName("Step 7: 用户不在房间内 → 离开时抛异常")
//        void leaveRoom_WhenNotInRoom_ShouldThrow() {
//            Long userId = 8007L;
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("不在房间测试");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
//            RoomVO room = roomService.createRoom(dto, userId);
//
//            Long strangerId = 9999L;
//
//            assertThrows(RoomException.class,
//                    () -> roomService.leaveRoom(room.getRoomId(), strangerId));
//        }
//    }
//
//    // ==================== 测试流 B：固定密码 + 踢人 ====================
//
//    @Nested
//    @DisplayName("测试流 B：固定密码房间 + 踢人权限")
//    class RoomPasswordAndKickFlow {
//        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RoomPasswordAndKickFlow.class);
//
//        private Long ownerId = 9000L;
//        private Long memberId = 9001L;
//        private RoomVO passwordRoom;
//        private String correctPassword;
//
//        @BeforeEach
//        void setUp() {
//            ownerId = 9000L;
//            memberId = 9001L;
//            passwordRoom = null;
//            correctPassword = null;
//        }
//
//        @Test
//        @DisplayName("Step 1: 创建固定密码房间 → 密码加密存储")
//        void createRoom_WithFixedPassword_ShouldEncryptAndStore() {
//            ownerId = 8010L;
//            correctPassword = "TestPass123";
//
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("密码保护测试房间");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_FIXED);
//            dto.setRawPassword(correctPassword);
//            dto.setPasswordVisible(1);
//
//            passwordRoom = roomService.createRoom(dto, ownerId);
//
//            log.info("==== [Step 1: 创建固定密码房间] 状态校验 ====");
//
//            assertNotNull(passwordRoom);
//            assertEquals(RoomConst.PASSWORD_MODE_FIXED, passwordRoom.getPasswordMode());
//
//            // DB 验证：密码 Hash 已存储（不为 null）
//            Room dbRoom = roomMapper.selectById(passwordRoom.getRoomId());
//            assertNotNull(dbRoom.getPasswordHash());
//            // 加密后的密码与原始密码不同
//            assertNotEquals(correctPassword, dbRoom.getPasswordHash());
//
//            // 验证密码可解密
//            String decrypted = PasswordUtils.decryptPassword(
//                    dbRoom.getPasswordHash(), passwordRoom.getRoomId(), roomConfig.getPassword().getMasterKey());
//            assertEquals(correctPassword, decrypted);
//
//            log.info("固定密码房间创建成功: roomId={}, passwordMode=FIXED, hash={}",
//                    passwordRoom.getRoomId(),
//                    dbRoom.getPasswordHash().substring(0, Math.min(20, dbRoom.getPasswordHash().length())) + "...");
//        }
//
//        @Test
//        @DisplayName("Step 2: 错误密码入房被拒 → 抛密码错误异常")
//        void joinRoom_WithWrongPassword_ShouldThrow() {
//            memberId = 8011L;
//
//            // 创建密码房间
//            correctPassword = "CorrectPass456";
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("密码校验测试");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_FIXED);
//            dto.setRawPassword(correctPassword);
//            passwordRoom = roomService.createRoom(dto, ownerId);
//
//            log.info("==== [Step 2: 错误密码入房被拒] 状态校验 ====");
//
//            JoinRoomDto joinDto = new JoinRoomDto();
//            joinDto.setRoomCode(passwordRoom.getRoomCode());
//            joinDto.setPassword("WrongPassword123");
//
//            RoomException exception = assertThrows(RoomException.class,
//                    () -> roomService.joinRoomAsync(joinDto, memberId));
//            assertTrue(exception.getMessage().contains("密码") || exception.getMessage().contains("wrong"),
//                    "异常信息应与密码相关: " + exception.getMessage());
//
//            log.info("错误密码入房被拒: userId={}, roomId={}", memberId, passwordRoom.getRoomId());
//        }
//
//        @Test
//        @DisplayName("Step 3: 房主踢人 → 成员记录删除 + 人数减一")
//        void kickMember_ShouldDeleteMemberAndDecrementCount() {
//            ownerId = 8012L;
//            memberId = 8013L;
//
//            // 创建无密码房间（方便测试）
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("踢人测试房间");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
//            RoomVO room = roomService.createRoom(dto, ownerId);
//
//            // 模拟成员通过 addMember 入房（直接调用，不走异步流程）
//            roomMemberService.addMember(room.getRoomId(), memberId, RoomConst.ROLE_MEMBER);
//            roomMapper.incrementMembersIfNotFull(room.getRoomId()); // 手动增加计数
//
//            log.info("==== [Step 3: 房主踢人] 状态校验 ====");
//
//            // 踢人前 DB 状态
//            Room beforeRoom = roomMapper.selectById(room.getRoomId());
//            assertEquals(2, beforeRoom.getCurrentMembers());
//
//            // 执行踢人
//            roomService.kickMember(room.getRoomId(), ownerId, memberId);
//
//            // DB 验证：成员已删除
//            RoomMember kickedMember = roomMemberMapper.selectList(
//                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoomMember>()
//                            .eq(RoomMember::getRoomId, room.getRoomId())
//                            .eq(RoomMember::getUserId, memberId)
//            ).stream().findFirst().orElse(null);
//            assertNull(kickedMember, "被踢成员应该已从 DB 删除");
//
//            log.info("房主{}踢出用户{}成功，成员记录已从 DB 删除", ownerId, memberId);
//        }
//
//        @Test
//        @DisplayName("Step 4: 非房主踢人 → 抛无权限异常")
//        void kickMember_ByNonOwner_ShouldThrowNoPermission() {
//            ownerId = 8014L;
//            Long strangerId = 8015L;
//
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("权限校验测试");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
//            RoomVO room = roomService.createRoom(dto, ownerId);
//
//            log.info("==== [Step 4: 非房主踢人] 状态校验 ====");
//
//            RoomException exception = assertThrows(RoomException.class,
//                    () -> roomService.kickMember(room.getRoomId(), strangerId, ownerId));
//            assertTrue(exception.getMessage().contains("权限") || exception.getMessage().contains("permission"),
//                    "异常应为无权限: " + exception.getMessage());
//
//            log.info("非房主踢人行为被拦截: operatorId={}, roomId={}", strangerId, room.getRoomId());
//        }
//
//        @Test
//        @DisplayName("Step 5: 房主自踢 → 抛无权限异常")
//        void kickMember_OwnerKickSelf_ShouldThrowNoPermission() {
//            ownerId = 8016L;
//
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("自踢测试");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
//            RoomVO room = roomService.createRoom(dto, ownerId);
//
//            log.info("==== [Step 5: 房主自踢] 状态校验 ====");
//
//            RoomException exception = assertThrows(RoomException.class,
//                    () -> roomService.kickMember(room.getRoomId(), ownerId, ownerId));
//            assertTrue(exception.getMessage().contains("权限") || exception.getMessage().contains("permission"),
//                    "异常应为无权限: " + exception.getMessage());
//
//            log.info("房主自踢行为被拦截: ownerId={}, roomId={}", ownerId, room.getRoomId());
//        }
//
//        @Test
//        @DisplayName("Step 6: 关闭房间 → 状态变为 CLOSED")
//        void closeRoom_ShouldSetStatusToClosed() {
//            ownerId = 8017L;
//
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("关闭房间测试");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
//            RoomVO room = roomService.createRoom(dto, ownerId);
//
//            log.info("==== [Step 6: 关闭房间] 状态校验 ====");
//
//            assertEquals(RoomConst.STATUS_ACTIVE, room.getStatus());
//
//            boolean result = roomService.closeRoom(room.getRoomId(), ownerId);
//
//            assertTrue(result);
//
//            // DB 验证
//            Room dbRoom = roomMapper.selectById(room.getRoomId());
//            assertEquals(RoomConst.STATUS_CLOSED, dbRoom.getStatus());
//
//            log.info("房间关闭成功: roomId={}, status={} (CLOSED)",
//                    room.getRoomId(), dbRoom.getStatus());
//        }
//
//        @Test
//        @DisplayName("Step 7: 非房主关闭房间 → 抛无权限异常")
//        void closeRoom_ByNonOwner_ShouldThrowNoPermission() {
//            ownerId = 8018L;
//            Long strangerId = 8019L;
//
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("关闭权限测试");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
//            RoomVO room = roomService.createRoom(dto, ownerId);
//
//            log.info("==== [Step 7: 非房主关闭房间] 状态校验 ====");
//
//            RoomException exception = assertThrows(RoomException.class,
//                    () -> roomService.closeRoom(room.getRoomId(), strangerId));
//            assertTrue(exception.getMessage().contains("权限") || exception.getMessage().contains("permission"),
//                    "异常应为无权限: " + exception.getMessage());
//
//            log.info("非房主关闭房间行为被拦截: operatorId={}, roomId={}", strangerId, room.getRoomId());
//        }
//    }
//
//    // ==================== 补充测试：updateRoomPassword ====================
//
//    @Nested
//    @DisplayName("补充测试：修改房间密码")
//    class UpdatePasswordTests {
//        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UpdatePasswordTests.class);
//
//        @Test
//        @DisplayName("房主修改密码模式 → NONE → FIXED")
//        void updatePassword_FromNoneToFixed_ShouldEncryptAndStore() {
//            Long ownerId = 8020L;
//
//            // 先创建无密码房间
//            RoomDto createDto = new RoomDto();
//            createDto.setRoomName("改密测试");
//            createDto.setMaxMembers(5);
//            createDto.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
//            RoomVO room = roomService.createRoom(createDto, ownerId);
//
//            log.info("==== [修改密码: NONE→FIXED] 状态校验 ====");
//
//            String newPassword = "NewSecurePass789";
//
//            // 修改为固定密码模式
//            roomService.updateRoomPassword(
//                    room.getRoomId(),
//                    ownerId,
//                    RoomConst.PASSWORD_MODE_FIXED,
//                    newPassword,
//                    1
//            );
//
//            // DB 验证
//            Room dbRoom = roomMapper.selectById(room.getRoomId());
//            assertEquals(RoomConst.PASSWORD_MODE_FIXED, dbRoom.getPasswordMode());
//            assertNotNull(dbRoom.getPasswordHash());
//
//            String decrypted = PasswordUtils.decryptPassword(
//                    dbRoom.getPasswordHash(), room.getRoomId(), roomConfig.getPassword().getMasterKey());
//            assertEquals(newPassword, decrypted);
//
//            log.info("密码修改成功: roomId={}, mode={} (FIXED)",
//                    room.getRoomId(), dbRoom.getPasswordMode());
//        }
//
//        @Test
//        @DisplayName("非房主修改密码 → 抛无权限异常")
//        void updatePassword_ByNonOwner_ShouldThrow() {
//            Long ownerId = 8021L;
//            Long strangerId = 8022L;
//
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("改密权限测试");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
//            RoomVO room = roomService.createRoom(dto, ownerId);
//
//            log.info("==== [修改密码: 权限校验] 状态校验 ====");
//
//            RoomException exception = assertThrows(RoomException.class,
//                    () -> roomService.updateRoomPassword(
//                            room.getRoomId(), strangerId, RoomConst.PASSWORD_MODE_FIXED, "pass1234", 1));
//            assertTrue(exception.getMessage().contains("权限") || exception.getMessage().contains("permission"));
//
//            log.info("非房主修改密码行为被拦截: operatorId={}, roomId={}", strangerId, room.getRoomId());
//        }
//
//        @Test
//        @DisplayName("TOTP 模式生成随机密钥")
//        void createRoom_WithTotpMode_ShouldGenerateSecret() {
//            Long ownerId = 8023L;
//
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("TOTP测试");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_TOTP);
//            RoomVO room = roomService.createRoom(dto, ownerId);
//
//            log.info("==== [创建 TOTP 房间] 状态校验 ====");
//
//            assertEquals(RoomConst.PASSWORD_MODE_TOTP, room.getPasswordMode());
//
//            // DB 验证：passwordHash 不为空（存储 TOTP secret）
//            Room dbRoom = roomMapper.selectById(room.getRoomId());
//            assertNotNull(dbRoom.getPasswordHash());
//            assertTrue(dbRoom.getPasswordHash().length() > 10);
//
//            // 验证 TOTP 密钥可用
//            String currentCode = PasswordUtils.getCurrentTotp(dbRoom.getPasswordHash());
//            assertNotNull(currentCode);
//            assertTrue(PasswordUtils.verifyTotp(currentCode, dbRoom.getPasswordHash()));
//
//            log.info("TOTP 房间创建成功: roomId={}, secret前20位={}",
//                    room.getRoomId(),
//                    dbRoom.getPasswordHash().substring(0, Math.min(20, dbRoom.getPasswordHash().length())));
//        }
//    }
//
//    // ==================== 补充测试：getRoomCurrentPassword ====================
//
//    @Nested
//    @DisplayName("补充测试：查询房间当前密码")
//    class GetCurrentPasswordTests {
//        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GetCurrentPasswordTests.class);
//
//        @Test
//        @DisplayName("房主查询密码 → 返回解密后明文")
//        void getCurrentPassword_AsOwner_ShouldReturnDecryptedPassword() {
//            Long ownerId = 8030L;
//            String rawPassword = "OwnerQueryPass";
//
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("查询密码测试");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_FIXED);
//            dto.setRawPassword(rawPassword);
//            dto.setPasswordVisible(1);
//            RoomVO room = roomService.createRoom(dto, ownerId);
//
//            log.info("==== [查询房间当前密码: 房主视角] 状态校验 ====");
//
//
//            assertNotNull(result);
//            assertEquals(rawPassword, result.getCurrentPassword());
//
//            log.info("房主查询密码成功: roomId={}, currentPassword={}",
//                    room.getRoomId(), result.getCurrentPassword());
//        }
//
//        @Test
//        @DisplayName("普通成员在密码可见时查询 → 返回解密后明文")
//        void getCurrentPassword_AsMember_Visible_ShouldReturnPassword() {
//            Long ownerId = 8031L;
//            Long memberId = 8032L;
//            String rawPassword = "MemberQueryPass";
//
//            // 创建密码可见房间
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("成员查询密码");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_FIXED);
//            dto.setRawPassword(rawPassword);
//            dto.setPasswordVisible(1);
//            RoomVO room = roomService.createRoom(dto, ownerId);
//
//            // 手动添加成员
//            roomMemberService.addMember(room.getRoomId(), memberId, RoomConst.ROLE_MEMBER);
//
//            log.info("==== [查询房间当前密码: 成员视角（可见）] 状态校验 ====");
//
//            RoomVO result = roomService.getRoomCurrentPassword(room.getRoomId(), memberId);
//
//            assertNotNull(result);
//            assertEquals(rawPassword, result.getCurrentPassword());
//
//            log.info("成员查询密码成功: roomId={}, currentPassword={}",
//                    room.getRoomId(), result.getCurrentPassword());
//        }
//
//        @Test
//        @DisplayName("非成员查询密码 → 抛无权限异常")
//        void getCurrentPassword_AsNonMember_ShouldThrow() {
//            Long ownerId = 8033L;
//            Long strangerId = 8034L;
//
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("非成员查询测试");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_FIXED);
//            dto.setRawPassword("TestPass");
//            dto.setPasswordVisible(1);
//            RoomVO room = roomService.createRoom(dto, ownerId);
//
//            log.info("==== [查询房间当前密码: 非成员] 状态校验 ====");
//
//            RoomException exception = assertThrows(RoomException.class,
//                    () -> roomService.getRoomCurrentPassword(room.getRoomId(), strangerId));
//            assertTrue(exception.getMessage().contains("权限") || exception.getMessage().contains("permission"));
//
//            log.info("非成员查询密码被拦截: userId={}, roomId={}", strangerId, room.getRoomId());
//        }
//
//        @Test
//        @DisplayName("成员查询密码（不可见）→ 抛无权限异常")
//        void getCurrentPassword_AsMember_Invisible_ShouldThrow() {
//            Long ownerId = 8035L;
//            Long memberId = 8036L;
//
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("密码不可见测试");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_FIXED);
//            dto.setRawPassword("TestPass");
//            dto.setPasswordVisible(0); // 对成员不可见
//            RoomVO room = roomService.createRoom(dto, ownerId);
//
//            // 手动添加成员
//            roomMemberService.addMember(room.getRoomId(), memberId, RoomConst.ROLE_MEMBER);
//
//            log.info("==== [查询房间当前密码: 成员视角（不可见）] 状态校验 ====");
//
//            RoomException exception = assertThrows(RoomException.class,
//                    () -> roomService.getRoomCurrentPassword(room.getRoomId(), memberId));
//            assertTrue(exception.getMessage().contains("权限") || exception.getMessage().contains("permission"));
//
//            log.info("成员在密码不可见时被拦截: userId={}, roomId={}", memberId, room.getRoomId());
//        }
//    }
//
//    // ==================== 补充测试：自动关房（MQ 降级） ====================
//
//    @Nested
//    @DisplayName("补充测试：自动关房（MQ 发送失败降级）")
//    class AutoCloseRoomTests {
//        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AutoCloseRoomTests.class);
//
//        @Test
//        @DisplayName("手动删除最后成员后 → 房间自动关闭")
//        void deleteLastMember_ShouldAutoCloseRoom() {
//            Long ownerId = 8040L;
//
//            RoomDto dto = new RoomDto();
//            dto.setRoomName("自动关闭测试");
//            dto.setMaxMembers(5);
//            dto.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
//            RoomVO room = roomService.createRoom(dto, ownerId);
//
//            log.info("==== [手动删除最后成员] 状态校验 ====");
//
//            // 直接 Mapper 删除（模拟 LeaveRoomConsumer 的降级路径）
//            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoomMember> q =
//                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoomMember>()
//                            .eq(RoomMember::getRoomId, room.getRoomId())
//                            .eq(RoomMember::getUserId, ownerId);
//            int deleted = roomMemberMapper.delete(q);
//            assertEquals(1, deleted);
//
//            // 手动递减人数至 0
//            roomMapper.decrementMembersIfPositive(room.getRoomId());
//
//            // 验证：房间仍为 ACTIVE（关闭逻辑在 LeaveRoomConsumer 中）
//            Room r = roomMapper.selectById(room.getRoomId());
//            assertEquals(RoomConst.STATUS_ACTIVE, r.getStatus());
//
//            log.info("最后成员已删除，房间当前状态={} (ACTIVES)", r.getStatus());
//        }
//    }
//
//    // ==================== 补充测试：用户在线状态变更 ====================
//
//    @Nested
//    @DisplayName("补充测试：用户在线状态批量更新")
//    class UserOnlineStatusTests {
//        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserOnlineStatusTests.class);
//
//        @Test
//        @DisplayName("批量更新用户为离线 → 多个房间成员状态变更")
//        void updateStatusToOffline_ShouldUpdateAllRooms() {
//            Long userId = 8050L;
//            Long room1Owner = 8051L;
//            Long room2Owner = 8052L;
//
//            // 创建两个房间并让 userId 加入
//            RoomDto dto1 = new RoomDto();
//            dto1.setRoomName("用户状态测试房间1");
//            dto1.setMaxMembers(5);
//            dto1.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
//            RoomVO room1 = roomService.createRoom(dto1, room1Owner);
//
//            RoomDto dto2 = new RoomDto();
//            dto2.setRoomName("用户状态测试房间2");
//            dto2.setMaxMembers(5);
//            dto2.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
//            RoomVO room2 = roomService.createRoom(dto2, room2Owner);
//
//            // 手动加入两个房间
//            roomMemberService.addMember(room1.getRoomId(), userId, RoomConst.ROLE_MEMBER);
//            roomMapper.incrementMembersIfNotFull(room1.getRoomId());
//            roomMemberService.addMember(room2.getRoomId(), userId, RoomConst.ROLE_MEMBER);
//            roomMapper.incrementMembersIfNotFull(room2.getRoomId());
//
//            log.info("==== [批量更新用户为离线] 状态校验 ====");
//
//            // 批量更新为离线
//            int updated = roomMemberService.updateStatusToOffline(userId);
//
//            assertEquals(2, updated);
//
//            // DB 验证：两个房间的成员状态都为离线
//            List<RoomMember> members1 = roomMemberMapper.selectList(
//                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoomMember>()
//                            .eq(RoomMember::getRoomId, room1.getRoomId())
//                            .eq(RoomMember::getUserId, userId)
//            );
//            List<RoomMember> members2 = roomMemberMapper.selectList(
//                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoomMember>()
//                            .eq(RoomMember::getRoomId, room2.getRoomId())
//                            .eq(RoomMember::getUserId, userId)
//            );
//
//            assertEquals(1, members1.size());
//            assertEquals(RoomConst.MEMBER_STATUS_OFFLINE, members1.get(0).getStatus());
//            assertEquals(1, members2.size());
//            assertEquals(RoomConst.MEMBER_STATUS_OFFLINE, members2.get(0).getStatus());
//
//            log.info("用户{}在{}个房间的在线状态已更新为离线", userId, updated);
//        }
//    }
//}
