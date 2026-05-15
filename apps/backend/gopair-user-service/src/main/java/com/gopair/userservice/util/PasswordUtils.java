package com.gopair.userservice.util;

import com.gopair.common.crypto.BCryptPasswordEncoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 密码工具类。
 *
 * @author gopair
 */
@Slf4j
@Component
public class PasswordUtils {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 加密密码：生成随机盐后进行 BCrypt 哈希。
     *
     * @param rawPassword 原始明文密码
     * @return BCrypt 哈希串，格式：$2a$10$[22字符盐][31字符哈希]（共 60 字符）
     */
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * 验证密码：提取哈希串中的盐，用该盐重算明文哈希，与存储哈希常数时间比对。
     *
     * @param rawPassword 提交的明文密码
     * @param encodedPassword 存储的 BCrypt 哈希串
     * @return 匹配返回 true，否则 false
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

}
