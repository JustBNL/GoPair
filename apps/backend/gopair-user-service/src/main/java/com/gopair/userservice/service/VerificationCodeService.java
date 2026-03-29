package com.gopair.userservice.service;

import com.gopair.userservice.enums.UserErrorCode;
import com.gopair.userservice.exception.UserException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务
 * <p>
 * 负责验证码的生成、存储（Redis）、发送和校验。
 * Redis Key 规范：
 * - 验证码：verify:code:{type}:{email}   TTL = expireSeconds
 * - 频率限制：verify:limit:{type}:{email} TTL = resendIntervalSeconds
 *
 * @author gopair
 */
@Service
public class VerificationCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_KEY_PREFIX = "verify:code:";
    private static final String LIMIT_KEY_PREFIX = "verify:limit:";

    private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;

    @Value("${gopair.verification-code.expire-seconds:300}")
    private long expireSeconds;

    @Value("${gopair.verification-code.resend-interval-seconds:60}")
    private long resendIntervalSeconds;

    public VerificationCodeService(StringRedisTemplate redisTemplate, EmailService emailService) {
        this.redisTemplate = redisTemplate;
        this.emailService = emailService;
    }

    /**
     * 发送验证码
     *
     * @param email 目标邮箱
     * @param type  场景类型：register / resetPassword
     */
    public void sendCode(String email, String type) {
        String limitKey = LIMIT_KEY_PREFIX + type + ":" + email;

        // 限流校验：使用 SETNX 原子操作，避免并发竞态导致频率限制失效
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(limitKey, "1", resendIntervalSeconds, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(isNew)) {
            throw new UserException(UserErrorCode.VERIFICATION_CODE_SEND_TOO_FREQUENT);
        }

        String code = generateCode();
        String codeKey = CODE_KEY_PREFIX + type + ":" + email;

        // 先发送邮件，成功后再写入验证码；若发送失败则删除限流 key，允许用户立即重试
        try {
            emailService.sendVerificationCode(email, code, type);
        } catch (UserException e) {
            redisTemplate.delete(limitKey);
            throw e;
        }

        redisTemplate.opsForValue().set(codeKey, code, expireSeconds, TimeUnit.SECONDS);
    }

    /**
     * 校验验证码，校验成功后自动删除
     *
     * @param email 目标邮箱
     * @param type  场景类型
     * @param code  用户输入的验证码
     */
    public void verifyCode(String email, String type, String code) {
        String codeKey = CODE_KEY_PREFIX + type + ":" + email;
        String stored = redisTemplate.opsForValue().get(codeKey);
        if (stored == null || !stored.equals(code)) {
            throw new UserException(UserErrorCode.VERIFICATION_CODE_INVALID);
        }
        redisTemplate.delete(codeKey);
    }

    /**
     * 生成6位数字验证码
     */
    private String generateCode() {
        int code = RANDOM.nextInt(900000) + 100000;
        return String.valueOf(code);
    }
}
