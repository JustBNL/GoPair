package com.gopair.messageservice.config;

import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 消息服务配置属性
 *
 * <p>绑定 application.yml 中 {@code gopair.message} 前缀的配置项，
 * 使配置值在代码中以强类型方式使用，避免硬编码。
 */
@Validated
@Data
@RefreshScope
@Component
@ConfigurationProperties(prefix = "gopair.message")
public class MessageProperties {

    /**
     * 消息内容最大长度（字符数）。
     * 对应配置项：gopair.message.max-content-length
     */
    @Positive
    private int maxContentLength = 2000;

    /**
     * WebSocket 最大连接数。
     * 对应配置项：gopair.message.max-websocket-connections
     */
    @Positive
    private int maxWebsocketConnections = 1000;

    /**
     * 消息历史保留天数。
     * 对应配置项：gopair.message.history-retention-days
     */
    @Positive
    @Min(1)
    @Max(365)
    private int historyRetentionDays = 30;

    /**
     * Redis 消息频道前缀。
     * 对应配置项：gopair.message.redis-channel-prefix
     */
    private String redisChannelPrefix = "message";

    /**
     * Emoji 消息内容的最大字符长度。
     * 兼容多码点 Emoji（如 ❤️ 等），默认值 8。
     * 对应配置项：gopair.message.emoji-max-length
     */
    @Positive
    @Min(1)
    @Max(50)
    private int emojiMaxLength = 8;
}
