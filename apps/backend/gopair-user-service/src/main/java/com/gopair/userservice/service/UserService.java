package com.gopair.userservice.service;

import com.gopair.common.core.PageResult;
import com.gopair.userservice.domain.dto.UserDto;
import com.gopair.userservice.domain.dto.auth.ForgotPasswordRequest;
import com.gopair.userservice.domain.dto.auth.LoginRequest;
import com.gopair.userservice.domain.dto.auth.RegisterRequest;
import com.gopair.userservice.domain.dto.auth.SendCodeRequest;
import com.gopair.userservice.domain.vo.UserVO;
import com.gopair.userservice.domain.vo.auth.LoginResponse;
import com.gopair.userservice.domain.vo.auth.RegisterResponse;

/**
 * 用户服务接口
 * 
 * @author gopair
 */
public interface UserService {

    /**
     * 发送邮箱验证码
     *
     * @param request 包含邮箱和场景类型
     */
    void sendVerificationCode(SendCodeRequest request);

    /**
     * 忘记密码（通过验证码重置）
     *
     * @param request 包含邮箱、验证码和新密码
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * 用户登录
     *
     * @param loginRequest 登录请求（包含邮箱和密码）
     * @return 用户信息和令牌
     */
    LoginResponse login(LoginRequest loginRequest);

    /**
     * 用户注册
     *
     * @param registerRequest 注册请求
     * @return 注册结果
     */
    RegisterResponse register(RegisterRequest registerRequest);
    
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

    /**
     * 注销账号（将账号状态设为已注销，并释放邮箱以便重新注册）
     *
     * @param userId 用户ID
     */
    void cancelAccount(Long userId);
} 