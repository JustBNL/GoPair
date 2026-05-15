package com.gopair.common.crypto;

import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * 基于 BCrypt 算法的密码编码器实现。
 *
 * @author gopair
 */
public class BCryptPasswordEncoder {

    /** BCrypt cost 工作因子，2 的指数，表示迭代次数（2^10 = 1024） */
    private static final int DEFAULT_COST = 10;

    /**
     * BCrypt 哈希格式正则。
     * 匹配：$2(a|y|b)$[cost]$[53字符盐+哈希]
     */
    private static final Pattern BCRYPT_PATTERN = Pattern.compile(
            "\\A\\$2(a|y|b)?\\$(\\d\\d)\\$[./0-9A-Za-z]{53}"
    );

    /** BCrypt 算法版本 */
    public enum BCryptVersion {
        $2A("$2a"),
        $2Y("$2y"),
        $2B("$2b");

        private final String version;

        BCryptVersion(String version) {
            this.version = version;
        }

        public String getVersion() {
            return this.version;
        }
    }

    /** 当前实例的 cost 值（log rounds） */
    private final int strength;

    /** BCrypt 版本标识 */
    private final BCryptVersion version;

    /** 随机数源（可注入用于测试的可预测随机数） */
    private final SecureRandom random;

    /**
     * 构造默认编码器，cost=10，版本=$2a。
     */
    public BCryptPasswordEncoder() {
        this(BCryptVersion.$2A, DEFAULT_COST, null);
    }

    /**
     * 构造带指定版本和 cost 的编码器。
     *
     * @param version BCrypt 版本（$2a、$2b、$2y）
     * @param strength log rounds
     */
    public BCryptPasswordEncoder(BCryptVersion version, int strength) {
        this(version, strength, null);
    }

    /**
     * 构造带版本、cost 和随机源的编码器。
     *
     * @param version BCrypt 版本（$2a、$2b、$2y）
     * @param strength log rounds
     * @param random 随机数源（可为 null 使用默认 SecureRandom）
     */
    public BCryptPasswordEncoder(BCryptVersion version, int strength, SecureRandom random) {
        this.version = version;
        this.strength = strength;
        this.random = random;
    }

    /**
     * 加密密码：生成随机盐，执行 BCrypt 哈希。
     *
     */
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("rawPassword cannot be null");
        }
        String salt = getSalt();
        return BCrypt.hashpw(rawPassword.toString(), salt);
    }

    /**
     * 生成 BCrypt 盐字符串。
     *
     * @return 格式：$[version]$[cost]$[22字符随机盐]
     */
    private String getSalt() {
        if (this.random != null) {
            return BCrypt.gensalt(this.strength, this.random);
        }
        return BCrypt.gensalt(this.strength);
    }

    /**
     * 验证明文密码是否与存储的哈希匹配。
     *
     * @param rawPassword 提交的明文密码
     * @param encodedPassword 存储的 BCrypt 哈希
     * @return 匹配返回 true
     */
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("rawPassword cannot be null");
        }
        if (encodedPassword == null || encodedPassword.isEmpty()) {
            return false;
        }
        if (!BCRYPT_PATTERN.matcher(encodedPassword).matches()) {
            return false;
        }
        return BCrypt.checkpw(rawPassword.toString(), encodedPassword);
    }
}
