package com.gopair.userservice.util;

import com.gopair.userservice.domain.dto.UserDto;
import com.gopair.userservice.domain.po.User;

import java.time.LocalDateTime;

/**
 * 测试数据构建工具类
 * 
 * @author gopair
 */
public class TestDataBuilder {

    /**
     * 创建默认的用户DTO
     */
    public static UserDto createDefaultUserDto() {
        UserDto userDto = new UserDto();
        userDto.setUsername("testuser");
        userDto.setPassword("123456");
        userDto.setEmail("test@gopair.com");
        userDto.setAvatar("http://example.com/avatar.jpg");
        userDto.setStatus('0');
        userDto.setRemark("测试用户");
        return userDto;
    }

    /**
     * 创建指定用户名的用户DTO
     */
    public static UserDto createUserDto(String username) {
        UserDto userDto = createDefaultUserDto();
        userDto.setUsername(username);
        userDto.setEmail(username + "@gopair.com");
        return userDto;
    }

    /**
     * 创建用户DTO（完整参数）
     */
    public static UserDto createUserDto(String username, String password, String email) {
        UserDto userDto = createDefaultUserDto();
        userDto.setUsername(username);
        userDto.setPassword(password);
        userDto.setEmail(email);
        return userDto;
    }

    /**
     * 创建默认的用户实体
     */
    public static User createDefaultUser() {
        User user = new User();
        user.setUserId(1L);
        user.setUsername("testuser");
        user.setPassword("$2a$10$encoded.password.hash");
        user.setEmail("test@gopair.com");
        user.setAvatar("http://example.com/avatar.jpg");
        user.setStatus('0');
        user.setRemark("测试用户");
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        return user;
    }

    /**
     * 创建指定ID的用户实体
     */
    public static User createUser(Long userId) {
        User user = createDefaultUser();
        user.setUserId(userId);
        user.setUsername("testuser" + userId);
        user.setEmail("test" + userId + "@gopair.com");
        return user;
    }

    /**
     * 创建用户实体（完整参数）
     */
    public static User createUser(Long userId, String username, String email) {
        User user = createDefaultUser();
        user.setUserId(userId);
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }

    /**
     * 创建登录用的用户DTO
     */
    public static UserDto createLoginDto(String username, String password) {
        UserDto userDto = new UserDto();
        userDto.setUsername(username);
        userDto.setPassword(password);
        return userDto;
    }

    /**
     * 创建无效的用户DTO（用于测试异常情况）
     */
    public static UserDto createInvalidUserDto() {
        UserDto userDto = new UserDto();
        // 不设置必要字段，用于测试验证
        return userDto;
    }

    /**
     * 创建批量用户DTO
     */
    public static UserDto[] createMultipleUserDto(int count) {
        UserDto[] users = new UserDto[count];
        for (int i = 0; i < count; i++) {
            users[i] = createUserDto("testuser" + (i + 1));
        }
        return users;
    }

    /**
     * 创建批量用户实体
     */
    public static User[] createMultipleUser(int count) {
        User[] users = new User[count];
        for (int i = 0; i < count; i++) {
            users[i] = createUser((long) (i + 1));
        }
        return users;
    }
} 