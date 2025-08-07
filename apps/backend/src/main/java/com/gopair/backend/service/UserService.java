package com.gopair.backend.service;

import com.gopair.backend.domain.po.User;
import com.gopair.backend.domain.vo.UserInfoVO;

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
     * 获取用户信息视图对象
     *
     * @param username 用户名
     * @return 用户信息视图对象
     */
    UserInfoVO getUserInfo(String username);
    
    /**
     * 注册用户
     *
     * @param user 用户信息
     * @return 注册结果
     */
    boolean registerUser(User user);
    
    /**
     * 更新用户信息
     *
     * @param user 用户信息
     * @return 更新结果
     */
    boolean updateUser(User user);
} 