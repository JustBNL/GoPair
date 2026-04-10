package com.gopair.messageservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 消息服务配置属性
 *
 * <p>绑定 application.yml 中 {@code gopair.message} 前缀的配置项，
 * 使配置值在代码中以强类型方式使用，避免硬编码。
 */
@Data
@ConfigurationProperties(prefix = "gopair.message")
public class MessageProperties {

    /**
     * 消息内容最大长度（字符数）。
     * 对应配置项：gopair.message.max-content-length
     */
    private int maxContentLength = 2000;

    /**
     * WebSocket 最大连接数。
     * 对应配置项：gopair.message.max-websocket-connections
     */
    private int maxWebsocketConnections = 1000;

    /**
     * 消息历史保留天数。
     * 对应配置项：gopair.message.history-retention-days
     */
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
    private int emojiMaxLength = 8;
}
