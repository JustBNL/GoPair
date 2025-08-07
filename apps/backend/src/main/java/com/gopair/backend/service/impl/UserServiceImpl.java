package com.gopair.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gopair.backend.domain.po.User;
import com.gopair.backend.domain.vo.UserInfoVO;
import com.gopair.backend.mapper.UserMapper;
import com.gopair.backend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户服务实现类
 * 
 * @author gopair
 */
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public User getUserByUsername(String username) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    public UserInfoVO getUserInfo(String username) {
        // 查询用户信息
        User user = getUserByUsername(username);
        if (user == null) {
            return null;
        }
        
        // 转换为视图对象
        UserInfoVO userInfoVO = new UserInfoVO();
        userInfoVO.setUserId(user.getUserId());
        userInfoVO.setUsername(user.getUsername());
        userInfoVO.setAvatar(user.getAvatar());
        
        return userInfoVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean registerUser(User user) {
        // 检查用户名是否已存在
        User existUser = getUserByUsername(user.getUsername());
        if (existUser != null) {
            return false;
        }
        
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
    public boolean updateUser(User user) {
        // 设置更新时间
        user.setUpdateTime(LocalDateTime.now());
        
        // 如果密码不为空，则加密
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        
        // 更新数据库
        return userMapper.updateById(user) > 0;
    }
} 