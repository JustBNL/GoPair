package com.gopair.userservice.config;

import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 邮件业务配置属性类
 *
 * @author gopair
 */
@Validated
@Data
@RefreshScope
@Component
@ConfigurationProperties(prefix = "gopair.email")
public class EmailConfig {

    /**
     * 发件人邮箱地址
     */
    @NotBlank
    private String from;
}
