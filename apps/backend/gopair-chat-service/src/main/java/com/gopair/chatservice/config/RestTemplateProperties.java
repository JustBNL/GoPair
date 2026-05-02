package com.gopair.chatservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RestTemplate 配置属性。
 *
 * <p>支持两种模式：
 * <ul>
 *   <li>使用 Nacos 服务名（如 http://user-service/user/）：配合 @LoadBalanced RestTemplate，
 *       自动解析为具体实例 IP:Port。</li>
 *   <li>使用固定 URL（如 http://192.168.0.102:65099/user/）：直接访问指定地址。</li>
 * </ul>
 *
 * <p>默认值使用 Nacos 服务发现模式。
 */
@Data
@Component
@ConfigurationProperties(prefix = "gopair.rest-template")
public class RestTemplateProperties {

    /**
     * 是否启用 RestTemplate 调用。
     */
    private boolean enabled = true;

    /**
     * User Service 的调用地址。
     */
    private String userServiceUrl = "http://user-service/user/";
}
