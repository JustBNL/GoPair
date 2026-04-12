package com.gopair.framework.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 自动配置
 *
 * * [核心策略]
 * - 仅在 RestTemplate 类存在时加载，适配非 Web 服务不引入该 Bean。
 * - 配合 @LoadBalanced 支持通过 Nacos 服务名（如 user-service）进行服务间 HTTP 调用。
 *
 * @author gopair
 */
@Configuration
@ConditionalOnClass(RestTemplate.class)
@ConditionalOnProperty(name = "gopair.rest-template.enabled", havingValue = "true", matchIfMissing = false)
public class RestTemplateConfiguration {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
