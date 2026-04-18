package com.gopair.adminservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.adminservice.base.AdminServiceTestConfig;
import com.gopair.adminservice.context.AdminContext;
import com.gopair.adminservice.context.AdminContextHolder;
import com.gopair.adminservice.domain.po.AdminAuditLog;
import com.gopair.adminservice.domain.po.AdminUser;
import com.gopair.adminservice.domain.po.Room;
import com.gopair.adminservice.domain.po.RoomFile;
import com.gopair.adminservice.domain.po.User;
import com.gopair.adminservice.domain.po.VoiceCall;
import com.gopair.adminservice.domain.po.VoiceCallParticipant;
import com.gopair.adminservice.filter.AdminAuthFilter;
import com.gopair.adminservice.mapper.AdminAuditLogMapper;
import com.gopair.adminservice.mapper.AdminUserMapper;
import com.gopair.adminservice.mapper.RoomFileMapper;
import com.gopair.adminservice.mapper.RoomMapper;
import com.gopair.adminservice.mapper.UserMapper;
import com.gopair.adminservice.mapper.VoiceCallMapper;
import com.gopair.adminservice.mapper.VoiceCallParticipantMapper;
import com.gopair.adminservice.service.DashboardService;
import com.gopair.adminservice.service.RoomManageService;
import com.gopair.adminservice.service.UserManageService;
import com.gopair.adminservice.service.FileManageService;
import com.gopair.adminservice.service.VoiceCallService;
import com.gopair.adminservice.service.MessageManageService;
import com.gopair.adminservice.service.AuditLogService;
import com.gopair.adminservice.service.AdminAuthService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 管理类 Controller 集成测试。
 *
 * * [核心策略]
 * - @WebMvcTest：加载各 Controller，快启动、无 DB。
 * - Mock 所有 Service：隔离 Controller 的参数解析、异常捕获、响应封装逻辑。
 * - Mock AdminAuthFilter：绕过敏证链，直接测试 Controller。
 * - 通过 MockBean 注入的 Mapper 构造假数据，用于 Service 层返回。
 *
 * @author gopair
 */
@WebMvcTest(controllers = {
        UserManageController.class,
        RoomManageController.class,
        FileManageController.class,
        VoiceCallController.class,
        DashboardController.class,
        AuditLogController.class,
        MessageManageController.class
})
@AutoConfigureMockMvc(addFilters = false)
@ComponentScan(excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {com.gopair.adminservice.filter.AdminAuthFilter.class}
))
@Import(com.gopair.adminservice.base.AdminServiceTestConfig.class)
@WithMockUser(username = "admin", roles = {"ADMIN"})
@DisplayName("管理类 Controller 集成测试")
class AdminManageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserManageService userManageService;

    @MockBean
    private RoomManageService roomManageService;

    @MockBean
    private FileManageService fileManageService;

    @MockBean
    private VoiceCallService voiceCallService;

    @MockBean
    private MessageManageService messageManageService;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private AdminAuthService adminAuthService;

    @MockBean
    private AdminAuthFilter adminAuthFilter;

    @MockBean
    private AdminUserMapper adminUserMapper;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private RoomMapper roomMapper;

    @MockBean
    private RoomFileMapper roomFileMapper;

    @MockBean
    private VoiceCallMapper voiceCallMapper;

    @MockBean
    private VoiceCallParticipantMapper participantMapper;

    @MockBean
    private AdminAuditLogMapper auditLogMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        AdminContextHolder.clear();
        AdminContextHolder.set(new AdminContext(1L, "testadmin"));
    }

    @AfterEach
    void tearDown() {
        AdminContextHolder.clear();
    }

    // ==================== UserManageController ====================

    @Nested
    @DisplayName("GET /admin/users/page — 用户分页查询")
    class GetUserPage {

        @Test
        @DisplayName("正常返回分页数据")
        void getUserPage_ShouldReturnPage() throws Exception {
            Page<User> mockPage = new Page<>(1, 20);
            mockPage.setTotal(1);
            mockPage.setRecords(java.util.List.of(createMockUser(1L, "TestUser")));

            when(userManageService.getUserPage(any())).thenReturn(mockPage);

            mockMvc.perform(get("/admin/users/page")
                            .param("pageNum", "1")
                            .param("pageSize", "20")
                            .param("keyword", "Test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1));
        }

        @Test
        @DisplayName("无 keyword 参数也能正常返回")
        void getUserPage_WithoutKeyword_ShouldReturnPage() throws Exception {
            Page<User> mockPage = new Page<>(1, 20);
            when(userManageService.getUserPage(any())).thenReturn(mockPage);

            mockMvc.perform(get("/admin/users/page")
                            .param("pageNum", "1")
                            .param("pageSize", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Nested
    @DisplayName("GET /admin/users/{userId} — 用户详情")
    class GetUserDetail {

        @Test
        @DisplayName("用户存在返回详情")
        void getUserDetail_WhenExists_ShouldReturnDetail() throws Exception {
            when(userManageService.getUserDetail(1L))
                    .thenReturn(Map.of(
                            "user", createMockUser(1L, "DetailUser"),
                            "roomCount", 2L,
                            "ownedRoomCount", 1L
                    ));

            mockMvc.perform(get("/admin/users/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.user").exists())
                    .andExpect(jsonPath("$.data.roomCount").value(2))
                    .andExpect(jsonPath("$.data.ownedRoomCount").value(1));
        }

        @Test
        @DisplayName("用户不存在返回 404")
        void getUserDetail_WhenNotFound_ShouldReturn404() throws Exception {
            when(userManageService.getUserDetail(999L))
                    .thenThrow(new IllegalArgumentException("用户不存在"));

            mockMvc.perform(get("/admin/users/999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.msg").value("用户不存在"));
        }
    }

    @Nested
    @DisplayName("POST /admin/users/{userId}/disable — 停用用户")
    class DisableUser {

        @Test
        @DisplayName("正常停用返回 200")
        void disableUser_ShouldReturn200() throws Exception {
            doNothing().when(userManageService).disableUser(1L);

            mockMvc.perform(post("/admin/users/1/disable"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("用户不存在返回 400")
        void disableUser_WhenNotFound_ShouldReturn400() throws Exception {
            doThrow(new IllegalArgumentException("用户不存在"))
                    .when(userManageService).disableUser(999L);

            mockMvc.perform(post("/admin/users/999/disable"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.msg").value("用户不存在"));
        }
    }

    @Nested
    @DisplayName("POST /admin/users/{userId}/enable — 启用用户")
    class EnableUser {

        @Test
        @DisplayName("正常启用返回 200")
        void enableUser_ShouldReturn200() throws Exception {
            doNothing().when(userManageService).enableUser(1L);

            mockMvc.perform(post("/admin/users/1/enable"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== RoomManageController ====================

    @Nested
    @DisplayName("GET /admin/rooms/page — 房间分页查询")
    class GetRoomPage {

        @Test
        @DisplayName("正常返回分页数据")
        void getRoomPage_ShouldReturnPage() throws Exception {
            Page<Room> mockPage = new Page<>(1, 20);
            mockPage.setTotal(1);
            mockPage.setRecords(java.util.List.of(createMockRoom(1L, "TestRoom")));

            when(roomManageService.getRoomPage(any())).thenReturn(mockPage);

            mockMvc.perform(get("/admin/rooms/page")
                            .param("pageNum", "1")
                            .param("pageSize", "20")
                            .param("status", "0")
                            .param("keyword", "Test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1));
        }
    }

    @Nested
    @DisplayName("GET /admin/rooms/{roomId} — 房间详情")
    class GetRoomDetail {

        @Test
        @DisplayName("房间存在返回详情")
        void getRoomDetail_WhenExists_ShouldReturnDetail() throws Exception {
            when(roomManageService.getRoomDetail(1L))
                    .thenReturn(Map.of(
                            "room", createMockRoom(1L, "DetailRoom"),
                            "members", List.of(),
                            "userMap", Map.of()
                    ));

            mockMvc.perform(get("/admin/rooms/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.room").exists());
        }

        @Test
        @DisplayName("房间不存在返回 404")
        void getRoomDetail_WhenNotFound_ShouldReturn404() throws Exception {
            when(roomManageService.getRoomDetail(999L))
                    .thenThrow(new IllegalArgumentException("房间不存在"));

            mockMvc.perform(get("/admin/rooms/999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.msg").value("房间不存在"));
        }
    }

    @Nested
    @DisplayName("POST /admin/rooms/{roomId}/close — 强制关闭房间")
    class CloseRoom {

        @Test
        @DisplayName("正常关闭返回 200")
        void closeRoom_ShouldReturn200() throws Exception {
            doNothing().when(roomManageService).closeRoom(1L);

            mockMvc.perform(post("/admin/rooms/1/close"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("房间不存在返回 400")
        void closeRoom_WhenNotFound_ShouldReturn400() throws Exception {
            doThrow(new IllegalArgumentException("房间不存在"))
                    .when(roomManageService).closeRoom(999L);

            mockMvc.perform(post("/admin/rooms/999/close"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.msg").value("房间不存在"));
        }
    }

    // ==================== FileManageController ====================

    @Nested
    @DisplayName("GET /admin/files/page — 文件分页查询")
    class GetFilePage {

        @Test
        @DisplayName("正常返回分页数据")
        void getFilePage_ShouldReturnPage() throws Exception {
            Page<RoomFile> mockPage = new Page<>(1, 20);
            mockPage.setTotal(1);
            mockPage.setRecords(java.util.List.of(createMockFile(1L, "test.pdf")));

            when(fileManageService.getFilePage(any())).thenReturn(mockPage);

            mockMvc.perform(get("/admin/files/page")
                            .param("pageNum", "1")
                            .param("pageSize", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1));
        }
    }

    @Nested
    @DisplayName("GET /admin/files/{fileId} — 文件详情")
    class GetFileById {

        @Test
        @DisplayName("文件存在返回 RoomFile")
        void getFileById_WhenExists_ShouldReturnFile() throws Exception {
            when(fileManageService.getFileById(1L))
                    .thenReturn(createMockFile(1L, "report.pdf"));

            mockMvc.perform(get("/admin/files/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.fileName").value("report.pdf"));
        }

        @Test
        @DisplayName("文件不存在返回 404")
        void getFileById_WhenNotFound_ShouldReturn404() throws Exception {
            when(fileManageService.getFileById(999L)).thenReturn(null);

            mockMvc.perform(get("/admin/files/999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.msg").value("文件记录不存在"));
        }
    }

    @Nested
    @DisplayName("POST /admin/files/{fileId}/delete — 删除文件")
    class DeleteFile {

        @Test
        @DisplayName("正常删除返回 200")
        void deleteFile_ShouldReturn200() throws Exception {
            doNothing().when(fileManageService).deleteFile(1L);

            mockMvc.perform(post("/admin/files/1/delete"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("文件不存在返回 400")
        void deleteFile_WhenNotFound_ShouldReturn400() throws Exception {
            doThrow(new IllegalArgumentException("文件记录不存在"))
                    .when(fileManageService).deleteFile(999L);

            mockMvc.perform(post("/admin/files/999/delete"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.msg").value("文件记录不存在"));
        }
    }

    // ==================== VoiceCallController ====================

    @Nested
    @DisplayName("GET /admin/voice-calls/page — 通话分页查询")
    class GetVoiceCallPage {

        @Test
        @DisplayName("正常返回分页数据")
        void getVoiceCallPage_ShouldReturnPage() throws Exception {
            Page<VoiceCall> mockPage = new Page<>(1, 20);
            mockPage.setTotal(1);
            mockPage.setRecords(java.util.List.of(createMockVoiceCall(1L)));

            when(voiceCallService.getVoiceCallPage(any())).thenReturn(mockPage);

            mockMvc.perform(get("/admin/voice-calls/page")
                            .param("pageNum", "1")
                            .param("pageSize", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1));
        }
    }

    @Nested
    @DisplayName("GET /admin/voice-calls/{callId} — 通话详情")
    class GetVoiceCallById {

        @Test
        @DisplayName("通话存在返回详情")
        void getVoiceCallById_WhenExists_ShouldReturnCall() throws Exception {
            when(voiceCallService.getVoiceCallById(1L))
                    .thenReturn(createMockVoiceCall(1L));

            mockMvc.perform(get("/admin/voice-calls/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.callId").value(1));
        }

        @Test
        @DisplayName("通话不存在返回 404")
        void getVoiceCallById_WhenNotFound_ShouldReturn404() throws Exception {
            when(voiceCallService.getVoiceCallById(999L)).thenReturn(null);

            mockMvc.perform(get("/admin/voice-calls/999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.msg").value("通话记录不存在"));
        }
    }

    @Nested
    @DisplayName("GET /admin/voice-calls/{callId}/participants — 查询通话参与者")
    class GetParticipants {

        @Test
        @DisplayName("正常返回参与者列表")
        void getParticipants_ShouldReturnList() throws Exception {
            VoiceCallParticipant p1 = new VoiceCallParticipant();
            p1.setId(1L);
            p1.setCallId(1L);
            p1.setUserId(100L);

            when(voiceCallService.getParticipants(1L))
                    .thenReturn(List.of(p1));

            mockMvc.perform(get("/admin/voice-calls/1/participants"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].userId").value(100));
        }
    }

    // ==================== DashboardController ====================

    @Nested
    @DisplayName("GET /admin/dashboard/stats — 仪表盘统计")
    class GetStats {

        @Test
        @DisplayName("正常返回统计数据")
        void getStats_ShouldReturnDashboardStats() throws Exception {
            DashboardService.DashboardStats stats =
                    new DashboardService.DashboardStats(100L, 5L, 20L, 3L, 0L, 300L);

            when(dashboardService.getStats()).thenReturn(stats);

            mockMvc.perform(get("/admin/dashboard/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.totalUsers").value(100))
                    .andExpect(jsonPath("$.data.activeRooms").value(20))
                    .andExpect(jsonPath("$.data.todayMessages").value(0))
                    .andExpect(jsonPath("$.data.todayVoiceCallDuration").value(300));
        }
    }

    // ==================== AuditLogController ====================

    @Nested
    @DisplayName("GET /admin/audit-logs/page — 审计日志分页查询")
    class GetAuditLogPage {

        @Test
        @DisplayName("正常返回分页数据")
        void getAuditLogPage_ShouldReturnPage() throws Exception {
            Page<AdminAuditLog> mockPage = new Page<>(1, 20);
            mockPage.setTotal(0);

            when(auditLogService.getAuditLogPage(any())).thenReturn(mockPage);

            mockMvc.perform(get("/admin/audit-logs/page")
                            .param("pageNum", "1")
                            .param("pageSize", "20")
                            .param("adminId", "1")
                            .param("operation", "USER_DISABLE")
                            .param("targetType", "USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(0));
        }
    }

    // ==================== MessageManageController ====================

    @Nested
    @DisplayName("GET /admin/messages/page — 消息分页查询")
    class GetMessagePage {

        @Test
        @DisplayName("正常返回分页数据")
        void getMessagePage_ShouldReturnPage() throws Exception {
            com.gopair.adminservice.domain.po.Message msg =
                    new com.gopair.adminservice.domain.po.Message();
            msg.setMessageId(1L);
            msg.setContent("Hello");
            msg.setRoomId(1L);

            Page<com.gopair.adminservice.domain.po.Message> mockPage = new Page<>(1, 20);
            mockPage.setTotal(1);
            mockPage.setRecords(java.util.List.of(msg));

            when(messageManageService.getMessagePage(any())).thenReturn(mockPage);

            mockMvc.perform(get("/admin/messages/page")
                            .param("pageNum", "1")
                            .param("pageSize", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1));
        }
    }

    @Nested
    @DisplayName("GET /admin/messages/room/{roomId} — 按房间查询消息")
    class GetMessageByRoom {

        @Test
        @DisplayName("正常返回房间消息列表")
        void getMessageByRoom_ShouldReturnPage() throws Exception {
            Page<com.gopair.adminservice.domain.po.Message> mockPage = new Page<>(1, 50);
            mockPage.setTotal(0);

            when(messageManageService.getMessageByRoom(eq(1L), anyInt(), anyInt()))
                    .thenReturn(mockPage);

            mockMvc.perform(get("/admin/messages/room/1")
                            .param("pageNum", "1")
                            .param("pageSize", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== Mock 数据构造 ====================

    private User createMockUser(Long userId, String nickname) {
        User user = new User();
        user.setUserId(userId);
        user.setNickname(nickname);
        user.setEmail(nickname.toLowerCase() + "@test.com");
        user.setStatus('0');
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        return user;
    }

    private Room createMockRoom(Long roomId, String roomName) {
        Room room = new Room();
        room.setRoomId(roomId);
        room.setRoomName(roomName);
        room.setRoomCode("CODE" + roomId);
        room.setOwnerId(1L);
        room.setMaxMembers(10);
        room.setCurrentMembers(0);
        room.setStatus(0);
        room.setCreateTime(LocalDateTime.now());
        room.setUpdateTime(LocalDateTime.now());
        return room;
    }

    private RoomFile createMockFile(Long fileId, String fileName) {
        RoomFile file = new RoomFile();
        file.setFileId(fileId);
        file.setRoomId(1L);
        file.setFileName(fileName);
        file.setFilePath("/files/" + fileId);
        file.setFileSize(1024L);
        file.setFileType("document");
        file.setUploadTime(LocalDateTime.now());
        file.setCreateTime(LocalDateTime.now());
        file.setUpdateTime(LocalDateTime.now());
        return file;
    }

    private VoiceCall createMockVoiceCall(Long callId) {
        VoiceCall vc = new VoiceCall();
        vc.setCallId(callId);
        vc.setRoomId(1L);
        vc.setInitiatorId(1L);
        vc.setCallType(1);
        vc.setStatus(1);
        vc.setDuration(60);
        vc.setStartTime(LocalDateTime.now());
        vc.setIsAutoCreated(false);
        return vc;
    }
}
