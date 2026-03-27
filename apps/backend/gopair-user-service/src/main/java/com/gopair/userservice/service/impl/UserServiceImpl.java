package com.gopair.userservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gopair.common.core.PageResult;
import com.gopair.common.enums.impl.CommonErrorCode;
import com.gopair.common.util.BeanCopyUtils;
import com.gopair.userservice.enums.UserErrorCode;
import com.gopair.userservice.exception.LoginException;
import com.gopair.userservice.exception.UserException;
import com.gopair.common.util.JwtUtils;
import com.gopair.userservice.util.PasswordUtils;
import com.gopair.userservice.config.JwtConfig;
import com.gopair.userservice.domain.dto.UserDto;
import com.gopair.userservice.domain.dto.auth.ForgotPasswordRequest;
import com.gopair.userservice.domain.dto.auth.LoginRequest;
import com.gopair.userservice.domain.dto.auth.RegisterRequest;
import com.gopair.userservice.domain.dto.auth.SendCodeRequest;
import com.gopair.userservice.domain.po.User;
import com.gopair.userservice.domain.vo.UserVO;
import com.gopair.userservice.domain.vo.auth.LoginResponse;
import com.gopair.userservice.domain.vo.auth.RegisterResponse;
import com.gopair.userservice.mapper.UserMapper;
import com.gopair.userservice.service.UserService;
import com.gopair.userservice.service.VerificationCodeService;
import com.gopair.framework.logging.annotation.LogRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;

/**
 * 用户服务实现类
 * 
 * @author gopair
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper,User> implements UserService {

    private final UserMapper userMapper;
    private final PasswordUtils passwordUtils;
    private final JwtConfig jwtConfig;
    private final VerificationCodeService verificationCodeService;

    public UserServiceImpl(UserMapper userMapper, PasswordUtils passwordUtils, JwtConfig jwtConfig,
                           VerificationCodeService verificationCodeService) {
        this.userMapper = userMapper;
        this.passwordUtils = passwordUtils;
        this.jwtConfig = jwtConfig;
        this.verificationCodeService = verificationCodeService;
    }
    
    /**
     * 根据昵称查询用户
     */
    private User getUserByNickname(String nickname) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getNickname, nickname);
        return userMapper.selectOne(queryWrapper);
    }
    
    /**
     * 根据邮箱查询用户
     */
    private User getUserByEmail(String email) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getEmail, email);
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "用户注册", module = "用户管理")
    public RegisterResponse register(RegisterRequest registerRequest) {
        // 校验注册验证码
        verificationCodeService.verifyCode(registerRequest.getEmail(), "register", registerRequest.getCode());

        // 检查昵称是否已存在
        if (StringUtils.hasText(registerRequest.getNickname()) && getUserByNickname(registerRequest.getNickname()) != null) {
            throw new UserException(UserErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        
        // 检查邮箱是否已存在
        if (StringUtils.hasText(registerRequest.getEmail()) && getUserByEmail(registerRequest.getEmail()) != null) {
            throw new UserException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        }
        
        // 转换为PO
        User user = BeanCopyUtils.copyBean(registerRequest, User.class);
        
        // 默认状态
        if (user.getStatus() == null) {
            user.setStatus('0');
        }
        user.setCreateTime(LocalDateTime.now());

        // 密码加密
        user.setPassword(passwordUtils.encode(user.getPassword()));
        
        // 插入
        int result = userMapper.insert(user);
        if (result > 0) {
            RegisterResponse registerResponse = BeanCopyUtils.copyBean(user, RegisterResponse.class);
            registerResponse.setMessage("注册成功");
            return registerResponse;
        } else {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "用户信息更新", module = "用户管理")
    public boolean updateUser(UserDto userDto) {
        // 存在性
        if (userDto.getUserId() == null || userMapper.selectById(userDto.getUserId()) == null) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }
        
        // 昵称唯一
        if (StringUtils.hasText(userDto.getNickname())) {
            User existingUser = getUserByNickname(userDto.getNickname());
            if (existingUser != null && !existingUser.getUserId().equals(userDto.getUserId())) {
                throw new UserException(UserErrorCode.NICKNAME_ALREADY_EXISTS);
            }
        }
        
        // 邮箱唯一
        if (StringUtils.hasText(userDto.getEmail())) {
            User existingUser = getUserByEmail(userDto.getEmail());
            if (existingUser != null && !existingUser.getUserId().equals(userDto.getUserId())) {
                throw new UserException(UserErrorCode.EMAIL_ALREADY_EXISTS);
            }
        }
        
        User user = BeanCopyUtils.copyBean(userDto, User.class);
        user.setUpdateTime(LocalDateTime.now());
        if (StringUtils.hasText(user.getPassword())) {
            // 修改密码时必须验证当前密码
            if (!StringUtils.hasText(userDto.getCurrentPassword())) {
                throw new UserException(UserErrorCode.PASSWORD_ERROR);
            }
            User currentUser = userMapper.selectById(userDto.getUserId());
            if (!passwordUtils.matches(userDto.getCurrentPassword(), currentUser.getPassword())) {
                throw new UserException(UserErrorCode.PASSWORD_ERROR);
            }
            user.setPassword(passwordUtils.encode(user.getPassword()));
        }
        return userMapper.updateById(user) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "用户删除", module = "用户管理", includeResult = true)
    public boolean deleteUser(Long userId) {
        if (userMapper.selectById(userId) == null) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }
        return userMapper.deleteById(userId) > 0;
    }

    @Override
    public UserVO getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }
        return BeanCopyUtils.copyBean(user, UserVO.class);
    }

    @Override
    public PageResult<UserVO> getUserPage(UserDto userDto) {
        // 使用 PageHelper 启动分页
        PageHelper.startPage(userDto.getPageNum(), userDto.getPageSize());

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(userDto.getNickname())) {
            queryWrapper.like(User::getNickname, userDto.getNickname());
        }
        if (StringUtils.hasText(userDto.getEmail())) {
            queryWrapper.like(User::getEmail, userDto.getEmail());
        }
        if (userDto.getStatus() != null) {
            queryWrapper.eq(User::getStatus, userDto.getStatus());
        }

        // PageHelper 会自动拦截下面的第一个 MyBatis 查询
        List<User> userList = userMapper.selectList(queryWrapper);

        // 将查询结果封装到 PageInfo 对象中，获取更详细的分页信息
        PageInfo<User> pageInfo = new PageInfo<>(userList);

        List<UserVO> userVOList = BeanCopyUtils.copyBeanList(pageInfo.getList(), UserVO.class);
        return new PageResult<>(userVOList, pageInfo.getTotal(), (long) pageInfo.getPageNum(), (long) pageInfo.getPageSize());
    }

    @Override
    @LogRecord(operation = "发送验证码", module = "用户认证")
    public void sendVerificationCode(SendCodeRequest request) {
        // 忘记密码场景需校验邮箱已注册
        if ("resetPassword".equals(request.getType())) {
            if (getUserByEmail(request.getEmail()) == null) {
                throw new UserException(UserErrorCode.EMAIL_NOT_EXISTS);
            }
        }
        verificationCodeService.sendCode(request.getEmail(), request.getType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "忘记密码", module = "用户认证")
    public void forgotPassword(ForgotPasswordRequest request) {
        // 校验验证码
        verificationCodeService.verifyCode(request.getEmail(), "resetPassword", request.getCode());
        // 查询用户
        User user = getUserByEmail(request.getEmail());
        if (user == null) {
            throw new UserException(UserErrorCode.EMAIL_NOT_EXISTS);
        }
        // 更新密码
        user.setPassword(passwordUtils.encode(request.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    @LogRecord(operation = "用户登录", module = "用户认证")
    public LoginResponse login(LoginRequest loginRequest) {
        // 登录：邮箱 + 密码
        if (!StringUtils.hasText(loginRequest.getEmail()) || !StringUtils.hasText(loginRequest.getPassword())) {
            throw new LoginException(CommonErrorCode.PARAM_MISSING);
        }
        User user = getUserByEmail(loginRequest.getEmail());
        if (user == null) {
            throw new LoginException(UserErrorCode.USER_NOT_FOUND);
        }
        if (!passwordUtils.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new LoginException(UserErrorCode.PASSWORD_ERROR);
        }
        String token = JwtUtils.generateToken(user.getNickname(), user.getUserId().toString(), 
                jwtConfig.getSecret(), jwtConfig.getExpiration());
        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setUserId(user.getUserId());
        loginResponse.setNickname(user.getNickname());
        loginResponse.setToken(token);
        return loginResponse;
    }
} 