package com.gopair.adminservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gopair.adminservice.domain.po.AdminUser;
import com.gopair.adminservice.mapper.AdminUserMapper;
import com.gopair.adminservice.util.AdminJwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 管理员认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${gopair.admin.jwt.secret}")
    private String jwtSecret;

    @Value("${gopair.admin.jwt.expiration}")
    private Long jwtExpiration;

    public record LoginResult(String token, Long adminId, String username, String nickname) {}

    public LoginResult login(String username, String password) {
        AdminUser admin = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, username)
        );
        if (admin == null) {
            throw new IllegalArgumentException("管理员账号不存在");
        }
        if (admin.getStatus() == 1) {
            throw new IllegalArgumentException("管理员账号已被停用");
        }
        if (!passwordEncoder.matches(password, admin.getPassword())) {
            throw new IllegalArgumentException("密码错误");
        }
        String token = AdminJwtUtils.generateToken(username, admin.getId(), jwtSecret, jwtExpiration);
        log.info("[AdminAuth] 管理员登录成功: username={}", username);
        return new LoginResult(token, admin.getId(), admin.getUsername(), admin.getNickname());
    }
}
