package com.gopair.userservice.service.impl;

import com.gopair.userservice.config.EmailConfig;
import com.gopair.userservice.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 邮件服务实现类
 *
 * @author gopair
 */
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailConfig emailConfig;

    public EmailServiceImpl(JavaMailSender mailSender, EmailConfig emailConfig) {
        this.mailSender = mailSender;
        this.emailConfig = emailConfig;
    }

    @Override
    public void sendVerificationCode(String toEmail, String code, String type) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(emailConfig.getFrom());
            helper.setTo(toEmail);
            helper.setSubject(buildSubject(type));
            helper.setText(buildContent(code, type), true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("邮件发送失败: " + e.getMessage(), e);
        }
    }

    private String buildSubject(String type) {
        return "resetPassword".equals(type) ? "【GoPair】重置密码验证码" : "【GoPair】注册验证码";
    }

    private String buildContent(String code, String type) {
        String action = "resetPassword".equals(type) ? "重置密码" : "注册";
        return "<div style='font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; padding: 32px; border: 1px solid #e8e8e8; border-radius: 8px;'>" +
               "<h2 style='color: #1a1a1a; margin-bottom: 8px;'>GoPair</h2>" +
               "<p style='color: #555; margin-bottom: 24px;'>您正在进行<strong>" + action + "</strong>操作，验证码为：</p>" +
               "<div style='background: #f5f5f5; border-radius: 6px; padding: 16px 24px; text-align: center; letter-spacing: 8px; font-size: 32px; font-weight: bold; color: #1677ff;'>" +
               code +
               "</div>" +
               "<p style='color: #888; font-size: 13px; margin-top: 24px;'>验证码 <strong>5 分钟</strong>内有效，请勿泄露给他人。</p>" +
               "<p style='color: #bbb; font-size: 12px;'>如非本人操作，请忽略此邮件。</p>" +
               "</div>";
    }
}
