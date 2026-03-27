package com.gopair.userservice.service;

/**
 * 邮件服务接口
 *
 * @author gopair
 */
public interface EmailService {

    /**
     * 发送验证码邮件
     *
     * @param toEmail 收件人邮箱
     * @param code    验证码
     * @param type    场景类型：register=注册，resetPassword=忘记密码
     */
    void sendVerificationCode(String toEmail, String code, String type);
}
