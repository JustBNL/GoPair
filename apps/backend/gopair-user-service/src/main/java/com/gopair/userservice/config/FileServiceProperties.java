package com.gopair.userservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件服务调用配置属性。
 *
 * <p>用于 user-service 在用户注销时通知 file-service 删除头像等 OSS 对象。
 * 默认使用 Nacos 服务名模式，通过 @LoadBalanced RestTemplate 解析为具体实例 IP:Port。
 *
 * @author gopair
 */
@Data
@Component
@ConfigurationProperties(prefix = "gopair.file-service")
public class FileServiceProperties {

    /**
     * File Service 的调用地址。
     *
     * <p>支持两种格式：
     * <ul>
     *   <li>Nacos 服务名模式（默认）：{@code http://file-service/}</li>
     *   <li>固定地址模式：{@code http://192.168.0.102:65098/}</li>
     * </ul>
     */
    private String url = "http://file-service/";
}
