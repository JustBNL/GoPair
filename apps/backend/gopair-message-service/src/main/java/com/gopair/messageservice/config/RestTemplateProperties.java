package com.gopair.messageservice.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RestTemplate 配置属性。
 *
 * <p>支持两种模式：
 * <ul>
 *   <li>使用 Nacos 服务名（如 http://room-service/room/）：配合 @LoadBalanced RestTemplate，
 *       自动解析为具体实例 IP:Port。</li>
 *   <li>使用固定 URL（如 http://192.168.0.102:65099/room/）：直接访问指定地址，
 *       适用于服务端口不固定的环境。</li>
 * </ul>
 *
 * <p>默认值使用 Nacos 服务发现模式（{@code http://room-service/room/}）。
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
     * Room Service 的调用地址。
     *
     * <p>支持两种格式：
     * <ul>
     *   <li>Nacos 服务名模式（默认）：{@code http://room-service/room/}</li>
     *   <li>固定地址模式：{@code http://192.168.0.102:65099/room/}</li>
     * </ul>
     */
    private String roomServiceUrl = "http://room-service/room/";

    /**
     * File Service 的调用地址。
     * 用于 message-service 在消息撤回时通知 file-service 删除 OSS 对象。
     */
    private String fileServiceUrl = "http://file-relay-service/";
}
