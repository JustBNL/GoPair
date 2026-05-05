package com.gopair.adminservice.controller;

import com.gopair.adminservice.base.AdminServiceTestConfig;
import com.gopair.adminservice.context.AdminContext;
import com.gopair.adminservice.context.AdminContextHolder;
import com.gopair.adminservice.domain.po.AdminUser;
import com.gopair.adminservice.filter.AdminAuthFilter;
import com.gopair.adminservice.mapper.AdminUserMapper;
import com.gopair.adminservice.enums.AdminErrorCode;
import com.gopair.adminservice.exception.AdminException;
import com.gopair.adminservice.service.AdminAuthService;
import com.gopair.common.core.R;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import com.gopair.framework.exception.GlobalExceptionHandler;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminAuthController 集成测试。
 *
 * * [核心策略]
 * - @WebMvcTest：仅加载 AdminAuthController，快启动、无 DB。
 * - Mock AdminAuthFilter：绕过敏证层，直接测试 Controller 逻辑。
 * - Mock AdminAuthService：隔离业务逻辑，测试 Controller 参数解析和响应封装。
 * - Mock AdminUserMapper + PasswordEncoder：模拟数据库查询 + 密码匹配。
 *
 * @author gopair
 */
@WebMvcTest(AdminAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({AdminServiceTestConfig.class, GlobalExceptionHandler.class})
@WithMockUser(username = "admin", roles = {"ADMIN"})
@DisplayName("AdminAuthController 集成测试")
class AdminAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminAuthService adminAuthService;

    @MockBean
    private AdminAuthFilter adminAuthFilter;

    @MockBean
    private AdminUserMapper adminUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        AdminContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        AdminContextHolder.clear();
    }

    @Nested
    @DisplayName("POST /admin/auth/login — 登录接口")
    class LoginEndpoint {

        @Test
        @DisplayName("正常登录返回 200 和 LoginResult")
        void login_WithValidCredentials_ShouldReturn200WithToken() throws Exception {
            String rawPassword = "admin123";
            String encodedPassword = passwordEncoder.encode(rawPassword);

            AdminUser adminUser = new AdminUser();
            adminUser.setId(1L);
            adminUser.setUsername("admin");
            adminUser.setPassword(encodedPassword);
            adminUser.setNickname("管理员");
            adminUser.setStatus(0);
            adminUser.setCreateTime(LocalDateTime.now());
            adminUser.setUpdateTime(LocalDateTime.now());

            when(adminUserMapper.selectOne(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(adminUser);

            AdminAuthService.LoginResult loginResult =
                    new AdminAuthService.LoginResult("mock.jwt.token", 1L, "admin", "管理员");
            when(adminAuthService.login(anyString(), anyString()))
                    .thenReturn(loginResult);

            mockMvc.perform(post("/admin/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", "admin")
                            .param("password", rawPassword))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.token").value("mock.jwt.token"))
                    .andExpect(jsonPath("$.data.adminId").value(1))
                    .andExpect(jsonPath("$.data.username").value("admin"));
        }

        @Test
        @DisplayName("账号不存在返回 200 和错误码 20000")
        void login_WithNonexistentUser_ShouldReturn200WithCode20000() throws Exception {
            when(adminAuthService.login(anyString(), anyString()))
                    .thenThrow(new AdminException(AdminErrorCode.ADMIN_NOT_FOUND));

            mockMvc.perform(post("/admin/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", "nonexistent")
                            .param("password", "anypass"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(20000))
                    .andExpect(jsonPath("$.msg").value("管理员账号不存在"));
        }

        @Test
        @DisplayName("密码错误返回 200 和错误码 20002")
        void login_WithWrongPassword_ShouldReturn200WithCode20002() throws Exception {
            when(adminAuthService.login(anyString(), anyString()))
                    .thenThrow(new AdminException(AdminErrorCode.ADMIN_PASSWORD_ERROR));

            mockMvc.perform(post("/admin/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", "admin")
                            .param("password", "wrongpassword"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(20002))
                    .andExpect(jsonPath("$.msg").value("密码错误"));
        }

        @Test
        @DisplayName("账号已停用返回 200 和错误码 20001")
        void login_WithDisabledAccount_ShouldReturn200WithCode20001() throws Exception {
            when(adminAuthService.login(anyString(), anyString()))
                    .thenThrow(new AdminException(AdminErrorCode.ADMIN_DISABLED));

            mockMvc.perform(post("/admin/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", "admin")
                            .param("password", "admin123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(20001))
                    .andExpect(jsonPath("$.msg").value("管理员账号已被停用"));
        }

        @Test
        @DisplayName("缺少 username 参数返回 400")
        void login_MissingUsername_ShouldReturn400() throws Exception {
            mockMvc.perform(post("/admin/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("password", "admin123"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("缺少 password 参数返回 400")
        void login_MissingPassword_ShouldReturn400() throws Exception {
            mockMvc.perform(post("/admin/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", "admin"))
                    .andExpect(status().isBadRequest());
        }
    }
}
