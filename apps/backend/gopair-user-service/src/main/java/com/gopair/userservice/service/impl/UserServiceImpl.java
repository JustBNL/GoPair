package com.gopair.userservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gopair.common.core.PageResult;
import com.gopair.common.util.BeanCopyUtils;
import com.gopair.userservice.domain.dto.UserDto;
import com.gopair.userservice.domain.po.User;
import com.gopair.userservice.domain.vo.UserVO;
import com.gopair.userservice.mapper.UserMapper;
import com.gopair.userservice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    @Autowired
    private UserMapper userMapper;

    private PasswordEncoder passwordEncoder;

    @Override
    public User getUserByUsername(String username) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    public User getUserById(Long userId) {
        return userMapper.selectById(userId);
    }

    @Override
    public User authenticateUser(String username, String password) {
        // 根据用户名查询用户
        User user = getUserByUsername(username);
        if (user == null) {
            return null;
        }

        // 验证密码
        if (passwordEncoder.matches(password, user.getPassword())) {
            return user;
        }
        
        return null;
    }

    @Override
    public boolean isUsernameExists(String username) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        return userMapper.selectCount(queryWrapper) > 0;
    }

    @Override
    public boolean isEmailExists(String email) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getEmail, email);
        return userMapper.selectCount(queryWrapper) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createUser(UserDto userDto) {
        // 使用BeanCopyUtils转换为PO对象
        User user = BeanCopyUtils.copyBean(userDto, User.class);
        
        // 设置默认值
        if (user.getStatus() == null) {
            user.setStatus('0'); // 默认正常状态
        }
        user.setCreateTime(LocalDateTime.now());

        // 密码加密
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        
        // 插入数据库
        return userMapper.insert(user) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUser(UserDto userDto) {
        // 使用BeanCopyUtils转换为PO对象
        User user = BeanCopyUtils.copyBean(userDto, User.class);
        
        // 设置更新时间
        user.setUpdateTime(LocalDateTime.now());
        
        // 如果密码不为空，则加密
        if (StringUtils.hasText(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        
        // 更新数据库
        return userMapper.updateById(user) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(Long userId) {
        return userMapper.deleteById(userId) > 0;
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
    public UserVO getUserVOById(Long userId) {
        User user = getUserById(userId);
        if (user == null) {
            return null;
        }
        
        // 使用BeanCopyUtils转换为VO对象
        return BeanCopyUtils.copyBean(user, UserVO.class);
    }
} 