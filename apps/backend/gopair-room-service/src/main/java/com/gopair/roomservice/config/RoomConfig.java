package com.gopair.roomservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.validation.annotation.Validated;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 房间配置属性类
 *
 * @author gopair
 */
@Validated
@Data
@RefreshScope
@Component
@ConfigurationProperties(prefix = "gopair.room")
public class RoomConfig {
    
    /**
     * 房间默认最大成员数
     */
    @Positive
    private Integer defaultMaxMembers = 10;
    
    /**
     * 房间默认过期时间（小时）
     */
    @Positive
    private Integer defaultExpireHours = 24;
    
    /**
     * 房间码长度
     */
    @Positive
    @Min(4)
    @Max(16)
    private Integer codeLength = 8;

    /**
     * 密码相关配置
     */
    @Valid
    private Password password = new Password();

    @Data
    public static class Password {
        /**
         * AES 主密钥，用于派生每个房间的加密密钥
         * 必须通过 ROOM_PASSWORD_MASTER_KEY 环境变量或 nacos:gopair-room.yml 配置注入
         */
        @NotBlank
        private String masterKey;
    }
} 