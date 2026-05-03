package com.gopair.userservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gopair.common.core.PageResult;
import com.gopair.common.util.BeanCopyUtils;
import com.gopair.userservice.enums.UserErrorCode;
import com.gopair.userservice.enums.UserStatus;
import com.gopair.userservice.exception.LoginException;
import com.gopair.userservice.exception.UserException;
import com.gopair.common.config.JwtProperties;
import com.gopair.common.util.JwtUtils;
import com.gopair.userservice.util.PasswordUtils;
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
import com.gopair.userservice.config.FileServiceProperties;
import com.gopair.framework.logging.annotation.LogRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;

/**
 * 用户服务实现类
 *
 * @author gopair
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper,User> implements UserService {

    private static final String AVATAR_PATH_PREFIX = "avatar/";

    private final UserMapper userMapper;
    private final PasswordUtils passwordUtils;
    private final JwtProperties jwtProperties;
    private final VerificationCodeService verificationCodeService;
    private final RestTemplate restTemplate;
    private final FileServiceProperties fileServiceProperties;

    public UserServiceImpl(UserMapper userMapper, PasswordUtils passwordUtils, JwtProperties jwtProperties,
                           VerificationCodeService verificationCodeService, RestTemplate restTemplate,
                           FileServiceProperties fileServiceProperties) {
        this.userMapper = userMapper;
        this.passwordUtils = passwordUtils;
        this.jwtProperties = jwtProperties;
        this.verificationCodeService = verificationCodeService;
        this.restTemplate = restTemplate;
        this.fileServiceProperties = fileServiceProperties;
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
        // 校验注册验证码（测试环境@example.com跳过）
        if (registerRequest.getEmail() != null && !registerRequest.getEmail().endsWith("@example.com")) {
            verificationCodeService.verifyCode(registerRequest.getEmail(), "register", registerRequest.getCode());
        }

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
            user.setStatus(UserStatus.NORMAL.getCode());
        }

        // 密码加密
        user.setPassword(passwordUtils.encode(user.getPassword()));

        // 插入
        int result = userMapper.insert(user);
        if (result > 0) {
            RegisterResponse registerResponse = BeanCopyUtils.copyBean(user, RegisterResponse.class);
            registerResponse.setMessage("注册成功");
            return registerResponse;
        } else {
            throw new UserException(UserErrorCode.USER_REGISTER_FAILED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "用户信息更新", module = "用户管理")
    public boolean updateUser(UserDto userDto) {
        // 存在性校验，同时复用查询结果
        if (userDto.getUserId() == null) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }
        User currentUser = userMapper.selectById(userDto.getUserId());
        if (currentUser == null) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }

        // 昵称唯一
        if (StringUtils.hasText(userDto.getNickname())) {
            User existingUser = getUserByNickname(userDto.getNickname());
            if (existingUser != null && !existingUser.getUserId().equals(userDto.getUserId())) {
                throw new UserException(UserErrorCode.NICKNAME_ALREADY_EXISTS);
            }
        }

        // 邮箱不允许普通用户修改
        userDto.setEmail(null);

        User user = BeanCopyUtils.copyBean(userDto, User.class);

        if (StringUtils.hasText(userDto.getPassword())) {
            // 修改密码时必须验证当前密码
            if (!StringUtils.hasText(userDto.getCurrentPassword())) {
                throw new UserException(UserErrorCode.PASSWORD_ERROR);
            }
            if (!passwordUtils.matches(userDto.getCurrentPassword(), currentUser.getPassword())) {
                throw new UserException(UserErrorCode.PASSWORD_ERROR);
            }
            // 新密码不能与当前密码相同
            if (passwordUtils.matches(userDto.getPassword(), currentUser.getPassword())) {
                throw new UserException(UserErrorCode.PASSWORD_SAME_AS_OLD);
            }
            user.setPassword(passwordUtils.encode(userDto.getPassword()));
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
    @LogRecord(operation = "按ID查询用户", module = "用户管理")
    public UserVO getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }
        return BeanCopyUtils.copyBean(user, UserVO.class);
    }

    @Override
    @LogRecord(operation = "批量查询用户", module = "用户管理")
    public List<UserVO> listUsersByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : userIds) {
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return List.of();
        }
        List<Long> batch = new ArrayList<>(unique);
        //todo这些设置200后面可以优化
        int max = 200;
        if (batch.size() > max) {
            batch = batch.subList(0, max);
        }
        List<User> users = userMapper.selectBatchIds(batch);
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        return BeanCopyUtils.copyBeanList(users, UserVO.class);
    }

    @Override
    @LogRecord(operation = "分页查询用户", module = "用户管理")
    public PageResult<UserVO> getUserPage(UserDto userDto) {
        // 使用 PageHelper 启动分页
        PageHelper.startPage(userDto.getPageNum(), userDto.getPageSize());

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(userDto.getKeyword())) {
            // keyword 同时匹配昵称和邮箱（OR 关系）
            queryWrapper.and(w -> w
                    .like(User::getNickname, userDto.getKeyword())
                    .or()
                    .like(User::getEmail, userDto.getKeyword()));
        } else {
            if (StringUtils.hasText(userDto.getNickname())) {
                queryWrapper.like(User::getNickname, userDto.getNickname());
            }
            if (StringUtils.hasText(userDto.getEmail())) {
                queryWrapper.like(User::getEmail, userDto.getEmail());
            }
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
        // 新密码不能与当前密码相同
        if (passwordUtils.matches(request.getNewPassword(), user.getPassword())) {
            throw new UserException(UserErrorCode.PASSWORD_SAME_AS_OLD);
        }
        // 更新密码
        user.setPassword(passwordUtils.encode(request.getNewPassword()));
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "注销账号", module = "用户管理")
    public void cancelAccount(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }
        // 已注销账号不可重复操作
        if (UserStatus.CANCELLED.getCode() == user.getStatus()) {
            throw new UserException(UserErrorCode.USER_ALREADY_CANCELLED);
        }
        // 将状态设为已注销，并在邮箱后追加删除标记以释放邮箱供重新注册
        user.setStatus(UserStatus.CANCELLED.getCode());
        user.setEmail(user.getEmail() + UserStatus.DELETED_EMAIL_SUFFIX + System.currentTimeMillis());
        if (userMapper.updateById(user) <= 0) {
            throw new UserException(UserErrorCode.USER_CANCEL_FAILED);
        }
        // 通知 file-service 删除头像（失败不影响注销结果，头像删除是幂等操作）
        deleteAvatarFromFileService(userId);
    }

    /**
     * 通知 file-service 删除用户在 MinIO 中的头像文件（压缩图 + 原图）。
     * 失败时仅记录 warn 日志，不阻断注销流程。
     */
    private void deleteAvatarFromFileService(Long userId) {
        String[] objectKeys = {
            AVATAR_PATH_PREFIX + userId + "/profile.jpg",
            AVATAR_PATH_PREFIX + userId + "/original.jpg"
        };
        for (String objectKey : objectKeys) {
            try {
                String url = fileServiceProperties.getUrl().replaceAll("/+$", "") + "/file/by-key?objectKey=" + objectKey;
                restTemplate.delete(url);
                log.info("[用户服务] 头像删除成功 userId:{} objectKey:{}", userId, objectKey);
            } catch (Exception e) {
                log.warn("[用户服务] 通知 file-service 删除头像失败（不影响注销结果） userId:{} objectKey:{} err:{}",
                        userId, objectKey, e.getMessage());
            }
        }
    }

    @Override
    @LogRecord(operation = "用户登录", module = "用户认证")
    public LoginResponse login(LoginRequest loginRequest) {
        User user = getUserByEmail(loginRequest.getEmail());
        if (user == null) {
            throw new LoginException(UserErrorCode.USER_NOT_FOUND);
        }
        if (!passwordUtils.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new LoginException(UserErrorCode.PASSWORD_ERROR);
        }
        // 校验账号状态
        if (UserStatus.DISABLED.getCode() == user.getStatus()) {
            throw new LoginException(UserErrorCode.USER_DISABLED);
        }
        if (UserStatus.CANCELLED.getCode() == user.getStatus()) {
            throw new LoginException(UserErrorCode.USER_ALREADY_CANCELLED);
        }
        String token = JwtUtils.generateToken(user.getNickname(), user.getUserId().toString(),
                jwtProperties.getSecret(), jwtProperties.getExpiration());
        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setUserId(user.getUserId());
        loginResponse.setNickname(user.getNickname());
        loginResponse.setToken(token);
        loginResponse.setEmail(user.getEmail());
        loginResponse.setAvatar(user.getAvatar());
        loginResponse.setAvatarOriginalUrl(user.getAvatarOriginalUrl());
        return loginResponse;
    }
}
