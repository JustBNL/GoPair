package com.gopair.userservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮件业务配置属性类
 *
 * @author gopair
 */
@Data
@Component
@ConfigurationProperties(prefix = "gopair.email")
public class EmailConfig {

    /**
     * 发件人邮箱地址
     */
    private String from;
}
