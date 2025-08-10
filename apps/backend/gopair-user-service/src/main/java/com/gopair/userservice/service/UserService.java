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
     * 根据ID查询用户
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    UserVO getUserById(Long userId);
    
    /**
     * 分页查询用户列表
     *
     * @param userDto 查询条件
     * @return 分页结果
     */
    PageResult<UserVO> getUserPage(UserDto userDto);
} 