package com.gopair.adminservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gopair.adminservice.domain.po.AdminUser;
import com.gopair.adminservice.enums.AdminErrorCode;
import com.gopair.adminservice.exception.AdminException;
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
            throw new AdminException(AdminErrorCode.ADMIN_NOT_FOUND);
        }
        if (admin.getStatus() == 1) {
            throw new AdminException(AdminErrorCode.ADMIN_DISABLED);
        }
        if (!passwordEncoder.matches(password, admin.getPassword())) {
            throw new AdminException(AdminErrorCode.ADMIN_PASSWORD_ERROR);
        }
        String token = AdminJwtUtils.generateToken(username, admin.getId(), jwtSecret, jwtExpiration);
        log.info("[AdminAuth] 管理员登录成功: username={}", username);
        return new LoginResult(token, admin.getId(), admin.getUsername(), admin.getNickname());
    }
}
