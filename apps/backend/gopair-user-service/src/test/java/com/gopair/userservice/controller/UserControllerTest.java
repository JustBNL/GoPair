package com.gopair.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.userservice.domain.dto.UserDto;
import com.gopair.userservice.domain.po.User;
import com.gopair.userservice.mapper.UserMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户控制器集成测试
 * 
 * @author gopair
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    private UserDto testUserDto;
    private Long createdUserId;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        testUserDto = new UserDto();
        testUserDto.setUsername("testuser");
        testUserDto.setPassword("123456");
        testUserDto.setEmail("test@gopair.com");
        testUserDto.setAvatar("http://example.com/avatar.jpg");
        testUserDto.setStatus('0');
        testUserDto.setRemark("测试用户");
    }

    @Test
    @Order(1)
    @DisplayName("测试创建用户")
    void testCreateUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testUserDto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true))
                .andReturn();

        // 验证数据库中是否创建成功
        User createdUser = userMapper.selectList(null).get(0);
        assertNotNull(createdUser);
        assertEquals(testUserDto.getUsername(), createdUser.getUsername());
        assertEquals(testUserDto.getEmail(), createdUser.getEmail());
        
        // 保存创建的用户ID供后续测试使用
        createdUserId = createdUser.getUserId();
    }

    @Test
    @Order(2)
    @DisplayName("测试用户登录")
    void testLogin() throws Exception {
        // 先创建用户
        testCreateUser();

        // 准备登录数据
        UserDto loginDto = new UserDto();
        loginDto.setUsername(testUserDto.getUsername());
        loginDto.setPassword(testUserDto.getPassword());

        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(testUserDto.getUsername()))
                .andExpect(jsonPath("$.data.email").value(testUserDto.getEmail()))
                .andExpect(jsonPath("$.data.token").exists());
    }

    @Test
    @Order(3)
    @DisplayName("测试根据ID查询用户")
    void testGetUserById() throws Exception {
        // 先创建用户
        testCreateUser();

        mockMvc.perform(get("/user/{userId}", createdUserId)
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(testUserDto.getUsername()))
                .andExpect(jsonPath("$.data.email").value(testUserDto.getEmail()));
    }

    @Test
    @Order(4)
    @DisplayName("测试更新用户")
    void testUpdateUser() throws Exception {
        // 先创建用户
        testCreateUser();

        // 准备更新数据
        testUserDto.setUserId(createdUserId);
        testUserDto.setEmail("updated@gopair.com");
        testUserDto.setRemark("更新后的用户");

        mockMvc.perform(put("/user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testUserDto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));

        // 验证更新结果
        User updatedUser = userMapper.selectById(createdUserId);
        assertEquals("updated@gopair.com", updatedUser.getEmail());
        assertEquals("更新后的用户", updatedUser.getRemark());
    }

    @Test
    @Order(5)
    @DisplayName("测试分页查询用户")
    void testGetUserPage() throws Exception {
        // 先创建几个用户
        for (int i = 1; i <= 3; i++) {
            UserDto dto = new UserDto();
            dto.setUsername("testuser" + i);
            dto.setPassword("123456");
            dto.setEmail("test" + i + "@gopair.com");
            dto.setStatus('0');
            
            mockMvc.perform(post("/user")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)));
        }

        mockMvc.perform(get("/user/page")
                .param("pageNo", "1")
                .param("pageSize", "2")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").exists())
                .andExpect(jsonPath("$.data.pageNo").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(2));
    }

    @Test
    @Order(6)
    @DisplayName("测试删除用户")
    void testDeleteUser() throws Exception {
        // 先创建用户
        testCreateUser();

        mockMvc.perform(delete("/user/{userId}", createdUserId)
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));

        // 验证用户已被删除
        User deletedUser = userMapper.selectById(createdUserId);
        assertNull(deletedUser);
    }

    @Test
    @DisplayName("测试异常场景 - 用户不存在")
    void testUserNotFound() throws Exception {
        mockMvc.perform(get("/user/{userId}", 99999L)
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("测试异常场景 - 无效的请求参数")
    void testInvalidParameters() throws Exception {
        UserDto invalidDto = new UserDto();
        // 不设置必要字段

        mockMvc.perform(post("/user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }
} 