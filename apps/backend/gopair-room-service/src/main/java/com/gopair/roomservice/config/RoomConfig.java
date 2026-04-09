package com.gopair.roomservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 房间配置属性类
 * 
 * @author gopair
 */
@Data
@Component
@ConfigurationProperties(prefix = "gopair.room")
public class RoomConfig {
    
    /**
     * 房间默认最大成员数
     */
    private Integer defaultMaxMembers = 10;
    
    /**
     * 房间默认过期时间（小时）
     */
    private Integer defaultExpireHours = 24;
    
    /**
     * 房间码长度
     */
    private Integer codeLength = 8;

    /**
     * 密码相关配置
     */
    private Password password = new Password();

    @Data
    public static class Password {
        /**
         * AES 主密钥，用于派生每个房间的加密密钥
         * 必须通过 ROOM_PASSWORD_MASTER_KEY 环境变量或 nacos:gopair-room.yml 配置注入
         */
        private String masterKey;
    }
} 