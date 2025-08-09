package com.gopair.userservice.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码工具类，用于密码加密和验证
 * 
 * @author gopair
 */
@Component
public class PasswordUtils {
    
    private final BCryptPasswordEncoder passwordEncoder;
    
    public PasswordUtils() {
        this.passwordEncoder = new BCryptPasswordEncoder();
    }
    
    /**
     * 加密密码
     *
     * @param rawPassword 原始密码
     * @return 加密后的密码
     */
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
    
    /**
     * 验证密码
     *
     * @param rawPassword 原始密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
} 