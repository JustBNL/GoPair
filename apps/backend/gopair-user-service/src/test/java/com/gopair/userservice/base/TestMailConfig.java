package com.gopair.userservice.base;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * 测试环境邮件配置
 *
 * 提供 JavaMailSenderImpl bean，但 send() 调用时会因连接 localhost:25 失败。
 * EmailServiceImpl.sendVerificationCode() 内部已用 try-catch 吞掉异常，
 * 因此邮件发送失败不会破坏测试的业务流程。
 */
@TestConfiguration
public class TestMailConfig {

    @Bean
    public JavaMailSenderImpl testJavaMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("localhost");
        sender.setPort(25);
        return sender;
    }
}
