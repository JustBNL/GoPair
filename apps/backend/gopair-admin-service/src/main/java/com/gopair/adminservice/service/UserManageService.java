package com.gopair.adminservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.adminservice.domain.po.Room;
import com.gopair.adminservice.domain.po.RoomMember;
import com.gopair.adminservice.domain.po.User;
import com.gopair.adminservice.enums.AdminErrorCode;
import com.gopair.adminservice.exception.AdminException;
import com.gopair.adminservice.mapper.RoomMapper;
import com.gopair.adminservice.mapper.RoomMemberMapper;
import com.gopair.adminservice.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManageService {

    private final UserMapper userMapper;
    private final RoomMemberMapper roomMemberMapper;
    private final RoomMapper roomMapper;

    public record UserPageQuery(Integer pageNum, Integer pageSize, String keyword, Character status) {}

    public Page<User> getUserPage(UserPageQuery query) {
        Page<User> page = new Page<>(query.pageNum(), query.pageSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.keyword())) {
            wrapper.and(w -> w.like(User::getNickname, query.keyword())
                    .or()
                    .like(User::getEmail, query.keyword()));
        }
        wrapper.orderByDesc(User::getCreateTime);
        if (query.status() != null) {
            wrapper.eq(User::getStatus, query.status());
        }
        return userMapper.selectPage(page, wrapper);
    }

    public User getUserById(Long userId) {
        return userMapper.selectById(userId);
    }

    public Map<String, Object> getUserDetail(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new AdminException(AdminErrorCode.USER_NOT_FOUND);
        }
        Map<String, Object> detail = new HashMap<>();
        detail.put("user", user);
        detail.put("roomCount", roomMemberMapper.selectCount(
                new LambdaQueryWrapper<RoomMember>().eq(RoomMember::getUserId, userId)
        ));
        detail.put("ownedRoomCount", roomMapper.selectCount(
                new LambdaQueryWrapper<Room>().eq(Room::getOwnerId, userId)
        ));
        return detail;
    }

    public void disableUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new AdminException(AdminErrorCode.USER_NOT_FOUND);
        }
        user.setStatus('1');
        userMapper.updateById(user);
        log.info("[UserManage] 停用用户: userId={}", userId);
    }

    public void enableUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new AdminException(AdminErrorCode.USER_NOT_FOUND);
        }
        user.setStatus('0');
        userMapper.updateById(user);
        log.info("[UserManage] 启用用户: userId={}", userId);
    }

    public void migrateEmail(Long userId, String newEmail) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new AdminException(AdminErrorCode.USER_NOT_FOUND);
        }
        String oldEmail = user.getEmail();

        LambdaQueryWrapper<User> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(User::getEmail, newEmail)
                .ne(userId != null, User::getUserId, userId);
        List<User> existUsers = userMapper.selectList(existWrapper);
        boolean normalUserExists = existUsers.stream()
                .anyMatch(u -> u.getStatus() != '2');
        if (normalUserExists) {
            throw new AdminException(AdminErrorCode.EMAIL_ALREADY_USED);
        }

        user.setEmail(newEmail);
        userMapper.updateById(user);
        log.info("[UserManage] 邮箱迁移: userId={}, oldEmail={}, newEmail={}", userId, oldEmail, newEmail);
    }

}
