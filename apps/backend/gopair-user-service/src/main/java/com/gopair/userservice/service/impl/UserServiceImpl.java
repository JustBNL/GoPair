package com.gopair.userservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gopair.common.core.PageResult;
import com.gopair.common.enums.impl.CommonErrorCode;
import com.gopair.common.enums.impl.UserErrorCode;
import com.gopair.common.exception.LoginException;
import com.gopair.common.exception.UserException;
import com.gopair.common.util.BeanCopyUtils;
import com.gopair.common.util.JwtUtils;
import com.gopair.userservice.util.PasswordUtils;
import com.gopair.userservice.config.JwtConfig;
import com.gopair.userservice.domain.dto.UserDto;
import com.gopair.userservice.domain.po.User;
import com.gopair.userservice.domain.vo.UserVO;
import com.gopair.userservice.mapper.UserMapper;
import com.gopair.userservice.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

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

    public UserServiceImpl(UserMapper userMapper, PasswordUtils passwordUtils, JwtConfig jwtConfig) {
        this.userMapper = userMapper;
        this.passwordUtils = passwordUtils;
        this.jwtConfig = jwtConfig;
    }
    
    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户实体
     */
    private User getUserByUsername(String username) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        return userMapper.selectOne(queryWrapper);
    }
    
    /**
     * 根据邮箱查询用户
     *
     * @param email 邮箱
     * @return 用户实体
     */
    private User getUserByEmail(String email) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getEmail, email);
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createUser(UserDto userDto) {
        // 检查用户名是否已存在
        if (getUserByUsername(userDto.getUsername()) != null) {
            throw new UserException(UserErrorCode.USERNAME_ALREADY_EXISTS);
        }
        
        // 检查邮箱是否已存在
        if (StringUtils.hasText(userDto.getEmail()) && getUserByEmail(userDto.getEmail()) != null) {
            throw new UserException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        }
        
        // 使用BeanCopyUtils转换为PO对象
        User user = BeanCopyUtils.copyBean(userDto, User.class);
        
        // 设置默认值
        if (user.getStatus() == null) {
            user.setStatus('0'); // 默认正常状态
        }
        user.setCreateTime(LocalDateTime.now());

        // 密码加密
        user.setPassword(passwordUtils.encode(user.getPassword()));
        
        // 插入数据库
        return userMapper.insert(user) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUser(UserDto userDto) {
        // 检查用户是否存在
        if (userDto.getUserId() == null || userMapper.selectById(userDto.getUserId()) == null) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }
        
        // 检查用户名是否已被其他用户使用
        if (StringUtils.hasText(userDto.getUsername())) {
            User existingUser = getUserByUsername(userDto.getUsername());
            if (existingUser != null && !existingUser.getUserId().equals(userDto.getUserId())) {
                throw new UserException(UserErrorCode.USERNAME_ALREADY_EXISTS);
            }
        }
        
        // 检查邮箱是否已被其他用户使用
        if (StringUtils.hasText(userDto.getEmail())) {
            User existingUser = getUserByEmail(userDto.getEmail());
            if (existingUser != null && !existingUser.getUserId().equals(userDto.getUserId())) {
                throw new UserException(UserErrorCode.EMAIL_ALREADY_EXISTS);
            }
        }
        
        // 使用BeanCopyUtils转换为PO对象
        User user = BeanCopyUtils.copyBean(userDto, User.class);
        
        // 设置更新时间
        user.setUpdateTime(LocalDateTime.now());
        
        // 如果密码不为空，则加密
        if (StringUtils.hasText(user.getPassword())) {
            user.setPassword(passwordUtils.encode(user.getPassword()));
        }
        
        // 更新数据库
        return userMapper.updateById(user) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(Long userId) {
        // 检查用户是否存在
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
        // 创建分页对象
        Page<User> page = new Page<>(userDto.getPageNum(), userDto.getPageSize());
        
        // 构建查询条件
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(userDto.getUsername())) {
            queryWrapper.like(User::getUsername, userDto.getUsername());
        }
        if (StringUtils.hasText(userDto.getEmail())) {
            queryWrapper.like(User::getEmail, userDto.getEmail());
        }
        if (userDto.getStatus() != null) {
            queryWrapper.eq(User::getStatus, userDto.getStatus());
        }
        
        // 执行分页查询
        IPage<User> userPage = userMapper.selectPage(page, queryWrapper);
        
        // 转换为VO对象
        List<UserVO> userVOList = BeanCopyUtils.copyBeanList(userPage.getRecords(), UserVO.class);
        
        // 返回分页结果
        return new PageResult<>(userVOList, userPage.getTotal(), userPage.getCurrent(), userPage.getSize());
    }

    @Override
    public UserVO login(UserDto userDto) {
        // 验证参数
        if (!StringUtils.hasText(userDto.getUsername()) || !StringUtils.hasText(userDto.getPassword())) {
            throw new LoginException(CommonErrorCode.PARAM_MISSING);
        }
        
        // 根据用户名查询用户
        User user = getUserByUsername(userDto.getUsername());
        if (user == null) {
            throw new LoginException(UserErrorCode.USER_NOT_FOUND);
        }
        
        // 验证密码
        if (!passwordUtils.matches(userDto.getPassword(), user.getPassword())) {
            throw new LoginException(UserErrorCode.INVALID_CREDENTIALS);
        }
        
        // 生成令牌
        String token = JwtUtils.generateToken(user.getUsername(), user.getUserId().toString(), 
                jwtConfig.getSecret(), jwtConfig.getExpiration());
        
        // 转换为VO对象
        UserVO userVO = BeanCopyUtils.copyBean(user, UserVO.class);
        // 设置令牌
        userVO.setToken(token);
        
        return userVO;
    }
} 