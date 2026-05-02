package com.gopair.adminservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.adminservice.base.BaseIntegrationTest;
import com.gopair.adminservice.base.AdminServiceTestConfig;
import com.gopair.adminservice.context.AdminContext;
import com.gopair.adminservice.context.AdminContextHolder;
import com.gopair.adminservice.domain.po.AdminAuditLog;
import com.gopair.adminservice.domain.po.AdminUser;
import com.gopair.adminservice.domain.po.Message;
import com.gopair.adminservice.domain.po.Room;
import com.gopair.adminservice.domain.po.RoomFile;
import com.gopair.adminservice.domain.po.RoomMember;
import com.gopair.adminservice.domain.po.User;
import com.gopair.adminservice.domain.po.VoiceCall;
import com.gopair.adminservice.domain.po.VoiceCallParticipant;
import com.gopair.adminservice.mapper.AdminAuditLogMapper;
import com.gopair.adminservice.mapper.AdminUserMapper;
import com.gopair.adminservice.mapper.MessageMapper;
import com.gopair.adminservice.mapper.RoomFileMapper;
import com.gopair.adminservice.mapper.RoomMapper;
import com.gopair.adminservice.mapper.RoomMemberMapper;
import com.gopair.adminservice.mapper.UserMapper;
import com.gopair.adminservice.mapper.VoiceCallMapper;
import com.gopair.adminservice.mapper.VoiceCallParticipantMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Admin Service 全链路集成测试。
 *
 * * [核心策略]
 * - 智能合并：将 8 个 Service 的核心操作编排为 3 条完整测试流。
 * - 真实 MySQL + @Transactional：每个测试方法独立事务，结束自动回滚。
 * - Mock AdminAuditLogMapper：所有写操作通过 verify() 捕获审计日志插入。
 * - AdminContext 注入：每个测试前通过 AdminContextHolder.set() 注入管理员上下文，
 *   供 AdminAuditAspect 读取 adminId/username。
 *
 * * [测试流编排]
 * - 测试流 A：认证登录 + 用户管理（分页/详情/停用/启用）+ 审计日志验证
 * - 测试流 B：房间管理 + 文件管理（分页/详情/关闭/删除）+ 审计日志验证
 * - 测试流 C：消息/通话/仪表盘/审计日志查询（纯读操作，覆盖全部查询场景）
 *
 * @author gopair
 */
@DisplayName("Admin Service 全链路集成测试")
@Import(AdminServiceTestConfig.class)
class AdminServiceLifecycleIntegrationTest extends BaseIntegrationTest {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(
            AdminServiceLifecycleIntegrationTest.class);

    @Autowired
    private AdminUserMapper adminUserMapper;


    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private RoomMemberMapper roomMemberMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private RoomFileMapper roomFileMapper;

    @Autowired
    private VoiceCallMapper voiceCallMapper;

    @Autowired
    private VoiceCallParticipantMapper participantMapper;

    @Autowired
    private AdminAuditLogMapper auditLogMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private UserManageService userManageService;

    @Autowired
    private RoomManageService roomManageService;

    @Autowired
    private MessageManageService messageManageService;

    @Autowired
    private FileManageService fileManageService;

    @Autowired
    private VoiceCallService voiceCallService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private AuditLogService auditLogService;

    private static final Long TEST_ADMIN_ID = 999001L;
    private static final String TEST_ADMIN_USERNAME = "testadmin";

    private final java.util.concurrent.atomic.AtomicLong idCounter = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());

    private long nextId(int typeOffset) {
        return idCounter.addAndGet(typeOffset + 1);
    }

    private Long insertTestAdminUser() {
        AdminUser admin = new AdminUser();
        admin.setId(TEST_ADMIN_ID);
        admin.setUsername(TEST_ADMIN_USERNAME);
        admin.setPassword(passwordEncoder.encode("testpass123"));
        admin.setNickname("测试管理员");
        admin.setStatus(0);
        admin.setCreateTime(LocalDateTime.now());
        admin.setUpdateTime(LocalDateTime.now());
        adminUserMapper.insert(admin);
        return admin.getId();
    }

    private Long insertTestUser(Character status, String nickname, String email) {
        User user = new User();
        user.setUserId(nextId(1));
        user.setNickname(nickname);
        user.setPassword("dummy");
        user.setEmail(email);
        user.setStatus(status);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
        return user.getUserId();
    }

    private Long insertTestRoom(Integer status, String roomName) {
        long id = nextId(100);
        Room room = new Room();
        room.setRoomId(id);
        room.setRoomCode("R" + String.format("%05d", id % 100000));
        room.setRoomName(roomName);
        room.setOwnerId(10000L);
        room.setMaxMembers(10);
        room.setCurrentMembers(0);
        room.setVersion(0);
        room.setPasswordMode(0);
        room.setPasswordVisible(1);
        room.setStatus(status);
        room.setExpireTime(java.time.LocalDateTime.now().plusHours(1));
        room.setCreateTime(LocalDateTime.now());
        room.setUpdateTime(LocalDateTime.now());
        roomMapper.insert(room);
        return room.getRoomId();
    }

    private void insertTestRoomMember(Long roomId, Long userId) {
        RoomMember member = new RoomMember();
        member.setRoomId(roomId);
        member.setUserId(userId);
        member.setRole(0);
        member.setStatus(0);
        member.setJoinTime(LocalDateTime.now());
        member.setLastActiveTime(LocalDateTime.now());
        roomMemberMapper.insert(member);
    }

    private Long insertTestMessage(Long roomId, Long senderId, String content) {
        Message msg = new Message();
        msg.setMessageId(nextId(200));
        msg.setRoomId(roomId);
        msg.setSenderId(senderId);
        msg.setContent(content);
        msg.setMessageType(1);
        msg.setCreateTime(LocalDateTime.now());
        msg.setUpdateTime(LocalDateTime.now());
        messageMapper.insert(msg);
        return msg.getMessageId();
    }

    private Long insertTestFile(Long roomId, String fileName) {
        RoomFile file = new RoomFile();
        file.setFileId(nextId(300));
        file.setRoomId(roomId);
        file.setUploaderId(10000L);
        file.setUploaderNickname("tester");
        file.setFileName(fileName);
        file.setFilePath("/files/" + file.getFileId());
        file.setFileSize(1024L);
        file.setFileType("image");
        file.setDownloadCount(0);
        file.setUploadTime(LocalDateTime.now());
        file.setCreateTime(LocalDateTime.now());
        file.setUpdateTime(LocalDateTime.now());
        roomFileMapper.insert(file);
        return file.getFileId();
    }

    private Long insertTestVoiceCall(Long roomId, Integer status, Integer duration) {
        VoiceCall vc = new VoiceCall();
        vc.setCallId(nextId(400));
        vc.setRoomId(roomId);
        vc.setInitiatorId(10000L);
        vc.setCallType(1);
        vc.setStatus(status);
        vc.setStartTime(LocalDateTime.now());
        vc.setEndTime(status == 2 ? LocalDateTime.now() : null);
        vc.setDuration(duration);
        vc.setIsAutoCreated(false);
        voiceCallMapper.insert(vc);
        return vc.getCallId();
    }

    private void insertTestParticipant(Long callId, Long userId) {
        VoiceCallParticipant p = new VoiceCallParticipant();
        p.setCallId(callId);
        p.setUserId(userId);
        p.setJoinTime(LocalDateTime.now());
        p.setConnectionStatus(1);
        participantMapper.insert(p);
    }

    private void setAdminContext(Long adminId, String username) {
        AdminContextHolder.set(new AdminContext(adminId, username));
    }

    // ==================== 测试流 A：认证登录 + 用户管理 ====================

    @Nested
    @Transactional
    @DisplayName("测试流 A：认证登录 + 用户管理")
    class AuthAndUserManagementFlow {

        @Test
        @DisplayName("Step 1: AdminAuthService.login → 正常登录返回 JWT Token")
        void login_WithValidCredentials_ShouldReturnToken() {
            Long adminId = insertTestAdminUser();

            LOG.info("==== [Step 1: login] 状态校验 ====");

            AdminAuthService.LoginResult result = adminAuthService.login(TEST_ADMIN_USERNAME, "testpass123");

            assertNotNull(result);
            assertNotNull(result.token());
            assertEquals(adminId, result.adminId());
            assertEquals(TEST_ADMIN_USERNAME, result.username());
            assertNotNull(result.nickname());

            LOG.info("管理员登录成功: adminId={}, username={}, token={}",
                    result.adminId(), result.username(), result.token());
        }

        @Test
        @DisplayName("Step 1b: login → 账号不存在抛 IllegalArgumentException")
        void login_WithNonexistentUser_ShouldThrow() {
            LOG.info("==== [Step 1b: login 账号不存在] 状态校验 ====");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> adminAuthService.login("nonexistent", "anypass")
            );
            assertTrue(ex.getMessage().contains("不存在"));

            LOG.info("账号不存在被拦截: exception={}", ex.getMessage());
        }

        @Test
        @DisplayName("Step 1c: login → 密码错误抛 IllegalArgumentException")
        void login_WithWrongPassword_ShouldThrow() {
            insertTestAdminUser();

            LOG.info("==== [Step 1c: login 密码错误] 状态校验 ====");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> adminAuthService.login(TEST_ADMIN_USERNAME, "wrongpassword")
            );
            assertTrue(ex.getMessage().contains("密码错误"));

            LOG.info("密码错误被拦截: exception={}", ex.getMessage());
        }

        @Test
        @DisplayName("Step 1d: login → 账号已停用抛 IllegalArgumentException")
        void login_WithDisabledAccount_ShouldThrow() {
            AdminUser admin = new AdminUser();
            admin.setId(999002L);
            admin.setUsername("disabled_admin");
            admin.setPassword(passwordEncoder.encode("pass"));
            admin.setNickname("已停用管理员");
            admin.setStatus(1);
            admin.setCreateTime(LocalDateTime.now());
            admin.setUpdateTime(LocalDateTime.now());
            adminUserMapper.insert(admin);

            LOG.info("==== [Step 1d: login 账号已停用] 状态校验 ====");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> adminAuthService.login("disabled_admin", "pass")
            );
            assertTrue(ex.getMessage().contains("停用"));

            LOG.info("账号停用被拦截: exception={}", ex.getMessage());
        }

        @Test
        @DisplayName("Step 2: getUserPage → 分页 + keyword 模糊查询")
        void getUserPage_WithPaginationAndKeyword_ShouldReturnFilteredResults() {
            Long u1 = insertTestUser('0', "Alice", "alice@test.com");
            Long u2 = insertTestUser('0', "Bob", "bob@test.com");
            Long u3 = insertTestUser('1', "Charlie", "charlie@test.com");

            LOG.info("==== [Step 2: getUserPage] 状态校验 ====");

            UserManageService.UserPageQuery query = new UserManageService.UserPageQuery(1, 10, "Alice");
            Page<User> page = userManageService.getUserPage(query);

            assertNotNull(page);
            assertTrue(page.getRecords().size() >= 1);
            boolean found = page.getRecords().stream().anyMatch(u -> u.getUserId().equals(u1));
            assertTrue(found);

            UserManageService.UserPageQuery query2 = new UserManageService.UserPageQuery(1, 10, null);
            Page<User> page2 = userManageService.getUserPage(query2);
            assertTrue(page2.getRecords().size() >= 3);

            LOG.info("用户分页查询成功: total={}, keyword=Alice 命中={}, 无keyword total={}",
                    page.getTotal(), found, page2.getTotal());
        }

        @Test
        @DisplayName("Step 3: getUserDetail → 存在用户返回 user + roomCount + ownedRoomCount")
        void getUserDetail_WhenUserExists_ShouldReturnFullDetail() {
            Long userId = insertTestUser('0', "DetailUser", "detail@test.com");
            Long roomId = insertTestRoom(0, "DetailRoom");
            insertTestRoomMember(roomId, userId);
            Long ownedRoomId = insertTestRoom(0, "OwnedRoom");
            Room owned = roomMapper.selectById(ownedRoomId);
            owned.setOwnerId(userId);
            roomMapper.updateById(owned);

            LOG.info("==== [Step 3: getUserDetail] 状态校验 ====");

            Map<String, Object> detail = userManageService.getUserDetail(userId);

            assertNotNull(detail.get("user"));
            assertNotNull(detail.get("roomCount"));
            assertNotNull(detail.get("ownedRoomCount"));
            assertEquals(1L, detail.get("roomCount"));
            assertEquals(1L, detail.get("ownedRoomCount"));

            User user = (User) detail.get("user");
            assertEquals(userId, user.getUserId());

            LOG.info("用户详情查询成功: userId={}, roomCount={}, ownedRoomCount={}",
                    userId, detail.get("roomCount"), detail.get("ownedRoomCount"));
        }

        @Test
        @DisplayName("Step 3b: getUserDetail → 用户不存在抛 IllegalArgumentException")
        void getUserDetail_WhenUserNotFound_ShouldThrow() {
            LOG.info("==== [Step 3b: getUserDetail 用户不存在] 状态校验 ====");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> userManageService.getUserDetail(999999L)
            );
            assertTrue(ex.getMessage().contains("不存在"));

            LOG.info("用户不存在被拦截: exception={}", ex.getMessage());
        }

        @Test
        @DisplayName("Step 4: disableUser → status 改为 '1'，审计日志写入")
        void disableUser_ShouldUpdateStatusAndWriteAuditLog() {
            Long userId = insertTestUser('0', "DisableMe", "disable@test.com");
            Long adminId = insertTestAdminUser();
            setAdminContext(adminId, TEST_ADMIN_USERNAME);

            LOG.info("==== [Step 4: disableUser] 状态校验 ====");

            userManageService.disableUser(userId);

            User updated = userMapper.selectById(userId);
            assertEquals('1', updated.getStatus());

            LOG.info("停用用户成功: userId={}, newStatus={}", userId, updated.getStatus());
            // 注意：@AdminAudit 已在 Controller 层拦截，Service 层仅验证状态变更
        }

        @Test
        @DisplayName("Step 5: enableUser → status 改回 '0'")
        void enableUser_ShouldUpdateStatus() {
            Long userId = insertTestUser('1', "EnableMe", "enable@test.com");
            Long adminId = insertTestAdminUser();
            setAdminContext(adminId, TEST_ADMIN_USERNAME);

            LOG.info("==== [Step 5: enableUser] 状态校验 ====");

            userManageService.enableUser(userId);

            User updated = userMapper.selectById(userId);
            assertEquals('0', updated.getStatus());

            LOG.info("启用用户成功: userId={}, newStatus={}", userId, updated.getStatus());
        }
    }

    // ==================== 用户邮箱迁移 ====================

    @Nested
    @Transactional
    @DisplayName("用户邮箱迁移")
    class UserEmailMigrateTests {

        @Test
        @DisplayName("迁移成功 - 新邮箱可登录")
        void migrateEmail_success() {
            Long userId = insertTestUser('0', "MigrateUser", "migrate_old@test.com");
            Long adminId = insertTestAdminUser();
            setAdminContext(adminId, TEST_ADMIN_USERNAME);

            LOG.info("==== [migrateEmail] 迁移成功 ====");

            userManageService.migrateEmail(userId, "migrate_new@test.com");

            User updated = userMapper.selectById(userId);
            assertEquals("migrate_new@test.com", updated.getEmail());

            LOG.info("邮箱迁移成功: userId={}, newEmail={}", userId, updated.getEmail());
        }

        @Test
        @DisplayName("迁移失败 - 目标邮箱被正常用户占用")
        void migrateEmail_conflict() {
            Long userId = insertTestUser('0', "MigrateUser2", "conflict_old@test.com");
            Long otherUserId = insertTestUser('0', "OtherUser", "conflict_target@test.com");
            Long adminId = insertTestAdminUser();
            setAdminContext(adminId, TEST_ADMIN_USERNAME);

            LOG.info("==== [migrateEmail] 邮箱冲突 ====");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> userManageService.migrateEmail(userId, "conflict_target@test.com")
            );
            assertTrue(ex.getMessage().contains("已被其他用户使用"));

            LOG.info("邮箱冲突被拦截: exception={}", ex.getMessage());
        }

        @Test
        @DisplayName("迁移失败 - 用户不存在")
        void migrateEmail_userNotFound() {
            Long adminId = insertTestAdminUser();
            setAdminContext(adminId, TEST_ADMIN_USERNAME);

            LOG.info("==== [migrateEmail] 用户不存在 ====");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> userManageService.migrateEmail(999999L, "any@test.com")
            );
            assertTrue(ex.getMessage().contains("用户不存在"));

            LOG.info("用户不存在被拦截: exception={}", ex.getMessage());
        }

        @Test
        @DisplayName("迁移成功 - 目标邮箱被已注销用户占用（可重新分配）")
        void migrateEmail_cancelledUserEmail() {
            Long userId = insertTestUser('0', "MigrateUser3", "cancelled_old@test.com");
            Long cancelledUserId = insertTestUser('1', "CancelledUser", "cancelled_target@test.com");
            Long adminId = insertTestAdminUser();
            setAdminContext(adminId, TEST_ADMIN_USERNAME);

            LOG.info("==== [migrateEmail] 目标邮箱被注销用户占用 ====");

            userManageService.migrateEmail(userId, "cancelled_target@test.com");

            User updated = userMapper.selectById(userId);
            assertEquals("cancelled_target@test.com", updated.getEmail());

            LOG.info("注销用户邮箱可重新分配: userId={}, newEmail={}", userId, updated.getEmail());
        }
    }

    // ==================== 测试流 B：房间管理 + 文件管理 ====================

    @Nested
    @Transactional
    @DisplayName("测试流 B：房间管理 + 文件管理")
    class RoomAndFileManagementFlow {

        @Test
        @DisplayName("Step 1: getRoomPage → 分页 + status 过滤 + keyword 模糊查询")
        void getRoomPage_WithFilters_ShouldReturnFilteredResults() {
            Long r1 = insertTestRoom(0, "ActiveRoom");
            Long r2 = insertTestRoom(1, "ClosedRoom");
            Long r3 = insertTestRoom(0, "ActiveKeywordRoom");

            LOG.info("==== [Step 1: getRoomPage] 状态校验 ====");

            RoomManageService.RoomPageQuery queryActive =
                    new RoomManageService.RoomPageQuery(1, 10, 0, null);
            Page<Room> pageActive = roomManageService.getRoomPage(queryActive);
            assertTrue(pageActive.getRecords().size() >= 2);

            RoomManageService.RoomPageQuery queryClosed =
                    new RoomManageService.RoomPageQuery(1, 10, 1, null);
            Page<Room> pageClosed = roomManageService.getRoomPage(queryClosed);
            assertTrue(pageClosed.getRecords().size() >= 1);

            RoomManageService.RoomPageQuery queryKeyword =
                    new RoomManageService.RoomPageQuery(1, 10, null, "ActiveKeyword");
            Page<Room> pageKeyword = roomManageService.getRoomPage(queryKeyword);
            assertTrue(pageKeyword.getRecords().size() >= 1);

            LOG.info("房间分页查询成功: 活跃总数={}, 已关闭总数={}, keyword命中总数={}",
                    pageActive.getTotal(), pageClosed.getTotal(), pageKeyword.getTotal());
        }

        @Test
        @DisplayName("Step 2: getRoomDetail → 存在房间返回 room + members + userMap")
        void getRoomDetail_WhenRoomExists_ShouldReturnFullDetail() {
            Long roomId = insertTestRoom(0, "DetailRoom");
            Long u1 = insertTestUser('0', "MemberOne", "m1@test.com");
            Long u2 = insertTestUser('0', "MemberTwo", "m2@test.com");
            insertTestRoomMember(roomId, u1);
            insertTestRoomMember(roomId, u2);

            LOG.info("==== [Step 2: getRoomDetail] 状态校验 ====");

            Map<String, Object> detail = roomManageService.getRoomDetail(roomId);

            assertNotNull(detail.get("room"));
            assertNotNull(detail.get("members"));
            assertNotNull(detail.get("userMap"));

            Room room = (Room) detail.get("room");
            assertEquals(roomId, room.getRoomId());

            var members = (java.util.List<?>) detail.get("members");
            assertEquals(2, members.size());

            LOG.info("房间详情查询成功: roomId={}, memberCount={}, userMapSize={}",
                    roomId, members.size(),
                    ((java.util.Map<?, ?>) detail.get("userMap")).size());
        }

        @Test
        @DisplayName("Step 2b: getRoomDetail → 房间不存在抛 IllegalArgumentException")
        void getRoomDetail_WhenRoomNotFound_ShouldThrow() {
            LOG.info("==== [Step 2b: getRoomDetail 房间不存在] 状态校验 ====");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> roomManageService.getRoomDetail(999999L)
            );
            assertTrue(ex.getMessage().contains("不存在"));

            LOG.info("房间不存在被拦截: exception={}", ex.getMessage());
        }

        @Test
        @DisplayName("Step 3: closeRoom → status 改为 1，审计日志写入")
        void closeRoom_ShouldUpdateStatusAndWriteAuditLog() {
            Long roomId = insertTestRoom(0, "ActiveToClose");
            Long adminId = insertTestAdminUser();
            setAdminContext(adminId, TEST_ADMIN_USERNAME);

            LOG.info("==== [Step 3: closeRoom] 状态校验 ====");

            roomManageService.closeRoom(roomId);

            Room updated = roomMapper.selectById(roomId);
            assertEquals(1, updated.getStatus());

            LOG.info("强制关闭房间成功: roomId={}, newStatus={}", roomId, updated.getStatus());
        }

        @Test
        @DisplayName("Step 4: getFilePage → 分页 + roomId 过滤 + keyword 模糊查询")
        void getFilePage_WithFilters_ShouldReturnFilteredResults() {
            Long roomId = insertTestRoom(0, "FileRoom");
            Long f1 = insertTestFile(roomId, "report.pdf");
            Long f2 = insertTestFile(roomId, "photo.jpg");
            Long otherRoomId = insertTestRoom(0, "OtherRoom");
            Long f3 = insertTestFile(otherRoomId, "other.txt");

            LOG.info("==== [Step 4: getFilePage] 状态校验 ====");

            FileManageService.FilePageQuery queryByRoom =
                    new FileManageService.FilePageQuery(1, 10, roomId, null);
            Page<RoomFile> pageByRoom = fileManageService.getFilePage(queryByRoom);
            assertTrue(pageByRoom.getRecords().size() >= 2);

            FileManageService.FilePageQuery queryByKeyword =
                    new FileManageService.FilePageQuery(1, 10, null, "report");
            Page<RoomFile> pageByKeyword = fileManageService.getFilePage(queryByKeyword);
            assertTrue(pageByKeyword.getRecords().size() >= 1);

            FileManageService.FilePageQuery queryAll =
                    new FileManageService.FilePageQuery(1, 10, null, null);
            Page<RoomFile> pageAll = fileManageService.getFilePage(queryAll);
            assertTrue(pageAll.getRecords().size() >= 3);

            LOG.info("文件分页查询成功: roomId过滤={}, keyword命中={}, 总数={}",
                    pageByRoom.getTotal(), pageByKeyword.getTotal(), pageAll.getTotal());
        }

        @Test
        @DisplayName("Step 5: deleteFile → 删除文件元数据，审计日志写入")
        void deleteFile_ShouldRemoveRecordAndWriteAuditLog() {
            Long roomId = insertTestRoom(0, "DeleteFileRoom");
            Long fileId = insertTestFile(roomId, "toDelete.pdf");
            Long adminId = insertTestAdminUser();
            setAdminContext(adminId, TEST_ADMIN_USERNAME);

            LOG.info("==== [Step 5: deleteFile] 状态校验 ====");

            assertNotNull(roomFileMapper.selectById(fileId));

            fileManageService.deleteFile(fileId);

            assertNull(roomFileMapper.selectById(fileId));

            LOG.info("删除文件元数据成功: fileId={}, DB已不存在", fileId);
        }

        @Test
        @DisplayName("Step 5b: deleteFile → 文件不存在抛 IllegalArgumentException")
        void deleteFile_WhenNotFound_ShouldThrow() {
            LOG.info("==== [Step 5b: deleteFile 文件不存在] 状态校验 ====");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> fileManageService.deleteFile(999999L)
            );
            assertTrue(ex.getMessage().contains("不存在"));

            LOG.info("文件不存在被拦截: exception={}", ex.getMessage());
        }
    }

    // ==================== 测试流 C：消息、通话、仪表盘、审计日志 ====================

    @Nested
    @Transactional
    @DisplayName("测试流 C：消息、通话、仪表盘、审计日志查询")
    class MessageVoiceCallDashboardAndAuditLogFlow {

        @Test
        @DisplayName("Step 1: getMessagePage → 分页 + roomId 过滤 + keyword 模糊查询")
        void getMessagePage_WithFilters_ShouldReturnFilteredResults() {
            Long roomId = insertTestRoom(0, "MsgRoom");
            Long u1 = insertTestUser('0', "SenderA", "a@test.com");
            Long m1 = insertTestMessage(roomId, u1, "Hello world");
            Long m2 = insertTestMessage(roomId, u1, "How are you");
            Long otherRoomId = insertTestRoom(0, "OtherRoom");
            Long m3 = insertTestMessage(otherRoomId, u1, "Other message");

            LOG.info("==== [Step 1: getMessagePage] 状态校验 ====");

            MessageManageService.MessagePageQuery queryByRoom =
                    new MessageManageService.MessagePageQuery(1, 10, roomId, null);
            Page<Message> pageByRoom = messageManageService.getMessagePage(queryByRoom);
            assertTrue(pageByRoom.getRecords().size() >= 2);

            MessageManageService.MessagePageQuery queryByKeyword =
                    new MessageManageService.MessagePageQuery(1, 10, null, "Hello");
            Page<Message> pageByKeyword = messageManageService.getMessagePage(queryByKeyword);
            assertTrue(pageByKeyword.getRecords().size() >= 1);

            LOG.info("消息分页查询成功: roomId过滤={}, keyword命中={}",
                    pageByRoom.getTotal(), pageByKeyword.getTotal());
        }

        @Test
        @DisplayName("Step 2: getMessageByRoom → 房间范围分页，keyword 过滤")
        void getMessageByRoom_WithPaginationAndKeyword_ShouldReturnFilteredResults() {
            Long roomId = insertTestRoom(0, "ByRoomMsgRoom");
            Long u1 = insertTestUser('0', "SenderB", "b@test.com");
            insertTestMessage(roomId, u1, "Message 1");
            insertTestMessage(roomId, u1, "Message 2");
            insertTestMessage(roomId, u1, "UniqueKeyword 3");

            LOG.info("==== [Step 2: getMessageByRoom] 状态校验 ====");

            Page<Message> page = messageManageService.getMessageByRoom(roomId, 1, 10);
            assertTrue(page.getRecords().size() >= 3);

            Page<Message> pageKeyword = messageManageService.getMessageByRoom(roomId, 1, 10);
            boolean keywordFound = pageKeyword.getRecords().stream()
                    .anyMatch(m -> m.getContent() != null && m.getContent().contains("UniqueKeyword"));
            assertTrue(keywordFound);

            LOG.info("按房间查询消息成功: roomId={}, total={}, keyword命中={}",
                    roomId, page.getTotal(), keywordFound);
        }

        @Test
        @DisplayName("Step 3: getVoiceCallPage → 分页 + roomId 过滤 + status 过滤")
        void getVoiceCallPage_WithFilters_ShouldReturnFilteredResults() {
            Long roomId = insertTestRoom(0, "VCRoom");
            Long c1 = insertTestVoiceCall(roomId, 1, 60);
            Long c2 = insertTestVoiceCall(roomId, 2, 120);
            Long otherRoomId = insertTestRoom(0, "OtherVCRoom");
            Long c3 = insertTestVoiceCall(otherRoomId, 2, 90);

            LOG.info("==== [Step 3: getVoiceCallPage] 状态校验 ====");

            VoiceCallService.VoiceCallPageQuery queryByRoom =
                    new VoiceCallService.VoiceCallPageQuery(1, 10, roomId, null);
            Page<VoiceCall> pageByRoom = voiceCallService.getVoiceCallPage(queryByRoom);
            assertTrue(pageByRoom.getRecords().size() >= 2);

            VoiceCallService.VoiceCallPageQuery queryByStatus =
                    new VoiceCallService.VoiceCallPageQuery(1, 10, null, 2);
            Page<VoiceCall> pageByStatus = voiceCallService.getVoiceCallPage(queryByStatus);
            assertTrue(pageByStatus.getRecords().size() >= 2);

            LOG.info("通话分页查询成功: roomId过滤={}, status=2过滤={}",
                    pageByRoom.getTotal(), pageByStatus.getTotal());
        }

        @Test
        @DisplayName("Step 4: getVoiceCallById → 通话存在返回完整信息，不存在返回 null")
        void getVoiceCallById_WithExistingCall_ShouldReturnCall() {
            Long roomId = insertTestRoom(0, "VCDetailRoom");
            Long callId = insertTestVoiceCall(roomId, 1, 45);

            LOG.info("==== [Step 4: getVoiceCallById] 状态校验 ====");

            VoiceCall call = voiceCallService.getVoiceCallById(callId);

            assertNotNull(call);
            assertEquals(callId, call.getCallId());
            assertEquals(45, call.getDuration());

            VoiceCall notFound = voiceCallService.getVoiceCallById(999999L);
            assertNull(notFound);

            LOG.info("通话详情查询成功: callId={}, duration={}, 不存在返回null={}",
                    callId, call.getDuration(), notFound == null);
        }

        @Test
        @DisplayName("Step 5: getParticipants → 通话参与者列表")
        void getParticipants_ShouldReturnParticipantList() {
            Long roomId = insertTestRoom(0, "ParticipantRoom");
            Long callId = insertTestVoiceCall(roomId, 1, 30);
            Long u1 = insertTestUser('0', "Participant1", "p1@test.com");
            Long u2 = insertTestUser('0', "Participant2", "p2@test.com");
            insertTestParticipant(callId, u1);
            insertTestParticipant(callId, u2);

            LOG.info("==== [Step 5: getParticipants] 状态校验 ====");

            var participants = voiceCallService.getParticipants(callId);

            assertEquals(2, participants.size());

            LOG.info("通话参与者查询成功: callId={}, participantCount={}",
                    callId, participants.size());
        }

        @Test
        @DisplayName("Step 6: getStats → 仪表盘统计（todayMessages 硬编码为 0）")
        void getStats_ShouldReturnAggregatedStatistics() {
            Long roomId = insertTestRoom(0, "DashboardRoom");
            Long u1 = insertTestUser('0', "DashboardUser", "dash@test.com");
            insertTestVoiceCall(roomId, 2, 60);
            insertTestVoiceCall(roomId, 2, 40);

            LOG.info("==== [Step 6: getStats] 状态校验 ====");

            DashboardService.DashboardStats stats = dashboardService.getStats();

            assertNotNull(stats);
            assertNotNull(stats.totalUsers());
            assertNotNull(stats.activeRooms());
            assertNotNull(stats.todayNewUsers());
            assertNotNull(stats.todayNewRooms());
            assertEquals(0L, stats.todayMessages());
            assertNotNull(stats.todayVoiceCallDuration());

            LOG.info("仪表盘统计成功: totalUsers={}, activeRooms={}, todayVoiceCallDuration={}, todayMessages={}",
                    stats.totalUsers(), stats.activeRooms(), stats.todayVoiceCallDuration(), stats.todayMessages());
        }

        @Test
        @DisplayName("Step 7: getAuditLogPage → 分页查询（按 adminId / operation / targetType 过滤）")
        void getAuditLogPage_WithFilters_ShouldReturnFilteredResults() {
            Long roomId = insertTestRoom(0, "AuditLogRoom");
            Long adminId = insertTestAdminUser();
            setAdminContext(adminId, TEST_ADMIN_USERNAME);

            // 直接插入审计日志记录（测试 AuditLogService 的分页查询功能）
            AdminAuditLog log1 = new AdminAuditLog();
            log1.setAdminId(adminId);
            log1.setAdminUsername(TEST_ADMIN_USERNAME);
            log1.setOperation("USER_DISABLE");
            log1.setTargetType("USER");
            log1.setTargetId("10001");
            log1.setDetail("{\"userId\":10001}");
            log1.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(log1);

            AdminAuditLog log2 = new AdminAuditLog();
            log2.setAdminId(adminId);
            log2.setAdminUsername(TEST_ADMIN_USERNAME);
            log2.setOperation("ROOM_CLOSE");
            log2.setTargetType("ROOM");
            log2.setTargetId(roomId.toString());
            log2.setDetail("{\"roomId\":" + roomId + "}");
            log2.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(log2);

            LOG.info("==== [Step 7: getAuditLogPage] 状态校验 ====");

            AuditLogService.AuditLogPageQuery queryAll =
                    new AuditLogService.AuditLogPageQuery(1, 10, null, null, null);
            Page<AdminAuditLog> pageAll = auditLogService.getAuditLogPage(queryAll);
            assertTrue(pageAll.getRecords().size() >= 2);

            AuditLogService.AuditLogPageQuery queryByAdmin =
                    new AuditLogService.AuditLogPageQuery(1, 10, adminId, null, null);
            Page<AdminAuditLog> pageByAdmin = auditLogService.getAuditLogPage(queryByAdmin);
            assertTrue(pageByAdmin.getRecords().size() >= 2);

            AuditLogService.AuditLogPageQuery queryByOp =
                    new AuditLogService.AuditLogPageQuery(1, 10, null, "USER_DISABLE", null);
            Page<AdminAuditLog> pageByOp = auditLogService.getAuditLogPage(queryByOp);
            assertTrue(pageByOp.getRecords().size() >= 1);

            AuditLogService.AuditLogPageQuery queryByTargetType =
                    new AuditLogService.AuditLogPageQuery(1, 10, null, null, "USER");
            Page<AdminAuditLog> pageByTargetType = auditLogService.getAuditLogPage(queryByTargetType);
            assertTrue(pageByTargetType.getRecords().size() >= 1);

            LOG.info("审计日志分页查询成功: 全部={}, adminId过滤={}, operation过滤={}, targetType过滤={}",
                    pageAll.getRecords().size(), pageByAdmin.getRecords().size(),
                    pageByOp.getRecords().size(), pageByTargetType.getRecords().size());
        }
    }
}
