package com.gopair.userservice.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gopair.userservice.domain.dto.UserDto;
import com.gopair.userservice.exception.UserException;
import com.gopair.userservice.domain.po.User;
import com.gopair.userservice.domain.vo.UserVO;
import com.gopair.userservice.mapper.UserMapper;
import com.gopair.userservice.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 用户服务单元测试
 * 
 * @author gopair
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;
    private UserDto testUserDto;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        testUser = new User();
        testUser.setUserId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword("$2a$10$encoded.password.hash");
        testUser.setEmail("test@gopair.com");
        testUser.setAvatar("http://example.com/avatar.jpg");
        testUser.setStatus('0');
        testUser.setRemark("测试用户");
        testUser.setCreateTime(LocalDateTime.now());
        testUser.setUpdateTime(LocalDateTime.now());

        testUserDto = new UserDto();
        testUserDto.setUsername("testuser");
        testUserDto.setPassword("123456");
        testUserDto.setEmail("test@gopair.com");
        testUserDto.setAvatar("http://example.com/avatar.jpg");
        testUserDto.setStatus('0');
        testUserDto.setRemark("测试用户");
    }

    @Test
    @DisplayName("测试创建用户 - 成功")
    void testCreateUser_Success() {
        // Given
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        // When
        Boolean result = userService.createUser(testUserDto);

        // Then
        assertTrue(result);
        verify(userMapper, times(1)).selectOne(any(QueryWrapper.class));
        verify(userMapper, times(1)).insert(any(User.class));
    }

    @Test
    @DisplayName("测试创建用户 - 用户名已存在")
    void testCreateUser_UsernameExists() {
        // Given
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(testUser);

        // When & Then
        assertThrows(UserException.class, () -> userService.createUser(testUserDto));
        verify(userMapper, times(1)).selectOne(any(QueryWrapper.class));
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("测试用户登录 - 成功")
    void testLogin_Success() {
        // Given
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(testUser);

        // When
        UserVO result = userService.login(testUserDto);

        // Then
        assertNotNull(result);
        assertEquals(testUser.getUsername(), result.getUsername());
        assertEquals(testUser.getEmail(), result.getEmail());
        assertNotNull(result.getToken());
        verify(userMapper, times(1)).selectOne(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("测试用户登录 - 用户不存在")
    void testLogin_UserNotExists() {
        // Given
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // When & Then
        assertThrows(UserException.class, () -> userService.login(testUserDto));
        verify(userMapper, times(1)).selectOne(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("测试用户登录 - 密码错误")
    void testLogin_WrongPassword() {
        // Given
        testUserDto.setPassword("wrongpassword");
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(testUser);

        // When & Then
        assertThrows(UserException.class, () -> userService.login(testUserDto));
        verify(userMapper, times(1)).selectOne(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("测试根据ID查询用户 - 成功")
    void testGetUserById_Success() {
        // Given
        when(userMapper.selectById(1L)).thenReturn(testUser);

        // When
        UserVO result = userService.getUserById(1L);

        // Then
        assertNotNull(result);
        assertEquals(testUser.getUsername(), result.getUsername());
        assertEquals(testUser.getEmail(), result.getEmail());
        verify(userMapper, times(1)).selectById(1L);
    }

    @Test
    @DisplayName("测试根据ID查询用户 - 用户不存在")
    void testGetUserById_NotExists() {
        // Given
        when(userMapper.selectById(99999L)).thenReturn(null);

        // When & Then
        assertThrows(UserException.class, () -> userService.getUserById(99999L));
        verify(userMapper, times(1)).selectById(99999L);
    }

    @Test
    @DisplayName("测试更新用户 - 成功")
    void testUpdateUser_Success() {
        // Given
        testUserDto.setUserId(1L);
        when(userMapper.selectById(1L)).thenReturn(testUser);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        // When
        Boolean result = userService.updateUser(testUserDto);

        // Then
        assertTrue(result);
        verify(userMapper, times(1)).selectById(1L);
        verify(userMapper, times(1)).updateById(any(User.class));
    }

    @Test
    @DisplayName("测试更新用户 - 用户不存在")
    void testUpdateUser_NotExists() {
        // Given
        testUserDto.setUserId(99999L);
        when(userMapper.selectById(99999L)).thenReturn(null);

        // When & Then
        assertThrows(UserException.class, () -> userService.updateUser(testUserDto));
        verify(userMapper, times(1)).selectById(99999L);
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("测试删除用户 - 成功")
    void testDeleteUser_Success() {
        // Given
        when(userMapper.selectById(1L)).thenReturn(testUser);
        when(userMapper.deleteById(1L)).thenReturn(1);

        // When
        Boolean result = userService.deleteUser(1L);

        // Then
        assertTrue(result);
        verify(userMapper, times(1)).selectById(1L);
        verify(userMapper, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("测试删除用户 - 用户不存在")
    void testDeleteUser_NotExists() {
        // Given
        when(userMapper.selectById(99999L)).thenReturn(null);

        // When & Then
        assertThrows(UserException.class, () -> userService.deleteUser(99999L));
        verify(userMapper, times(1)).selectById(99999L);
        verify(userMapper, never()).deleteById(anyLong());
    }
} 