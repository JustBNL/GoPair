package com.gopair.userservice.service;

import com.gopair.common.core.PageResult;
import com.gopair.userservice.domain.dto.UserDto;
import com.gopair.userservice.domain.po.User;
import com.gopair.userservice.domain.vo.UserVO;

/**
 * 用户服务接口
 * 
 * @author gopair
 */
public interface UserService {

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    User getUserByUsername(String username);
    
    /**
     * 根据用户ID查询用户
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    User getUserById(Long userId);
    
    /**
     * 用户认证
     *
     * @param username 用户名
     * @param password 密码
     * @return 用户信息，认证失败返回null
     */
    User authenticateUser(String username, String password);
    
    /**
     * 检查用户名是否存在
     *
     * @param username 用户名
     * @return 是否存在
     */
    boolean isUsernameExists(String username);
    
    /**
     * 检查邮箱是否存在
     *
     * @param email 邮箱
     * @return 是否存在
     */
    boolean isEmailExists(String email);
    
    /**
     * 创建用户
     *
     * @param userDto 用户DTO
     * @return 创建结果
     */
    boolean createUser(UserDto userDto);
    
    /**
     * 更新用户信息
     *
     * @param userDto 用户DTO
     * @return 更新结果
     */
    boolean updateUser(UserDto userDto);

    /**
     * 删除用户
     *
     * @param userId 用户ID
     * @return 删除结果
     */
    boolean deleteUser(Long userId);

    /**
     * 分页查询用户列表
     *
     * @param userDto 查询条件
     * @return 分页结果
     */
    PageResult<UserVO> getUserPage(UserDto userDto);

    /**
     * 根据ID获取用户VO
     *
     * @param userId 用户ID
     * @return 用户VO
     */
    UserVO getUserVOById(Long userId);
} 