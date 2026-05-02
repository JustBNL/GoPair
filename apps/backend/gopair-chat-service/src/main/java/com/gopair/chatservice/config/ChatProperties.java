package com.gopair.chatservice.config;

import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 聊天服务配置属性。
 *
 * <p>绑定 application.yml 中 {@code gopair.chat} 前缀的配置项。
 */
@Validated
@Data
@RefreshScope
@Component
@ConfigurationProperties(prefix = "gopair.chat")
public class ChatProperties {

    /**
     * 消息内容最大长度（字符数）。
     */
    @Positive
    private int maxContentLength = 2000;

    /**
     * 消息撤回时限（秒）。
     * 发送者可在消息发送后此时间范围内撤回，默认 120 秒（2分钟）。
     */
    @Positive
    private int recallTimeLimitSeconds = 120;

    /**
     * 私聊消息每页最大条数。
     */
    @Positive
    private int maxMessagePageSize = 50;
}
