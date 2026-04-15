package com.gopair.userservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 测试用邮件服务实现。
 * 不真正发送邮件，仅记录调用日志，确保验证码发送永不失败，
 * 使集成测试可以完整覆盖 Redis 写/消费链路而不被邮件网络问题阻塞。
 */
@Slf4j
@Service
@Primary
public class StubEmailServiceImpl implements EmailService {

    @Override
    public void sendVerificationCode(String toEmail, String code, String type) {
        log.debug("[StubEmailService] 模拟邮件发送: to={}, code={}, type={}", toEmail, code, type);
    }
}
