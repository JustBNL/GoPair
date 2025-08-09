package com.gopair.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j配置类
 * 
 * 配置API文档和JWT认证支持
 * 
 * @author gopair
 */
@Configuration
public class Knife4jConfig {

    /**
     * 创建API分组 - 认证授权
     */
    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
                .group("认证授权")
                .pathsToMatch("/api/auth/**")
                .build();
    }

    /**
     * 创建API分组 - 业务接口
     */
    @Bean
    public GroupedOpenApi businessApi() {
        return GroupedOpenApi.builder()
                .group("业务接口")
                .pathsToMatch("/api/**")
                .pathsToExclude("/api/auth/**")
                .build();
    }

    /**
     * 创建OpenAPI配置
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                // 添加JWT认证方案
                .components(new Components()
                        .addSecuritySchemes("Bearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .name("Authorization")
                                .description("JWT认证：请在下方输入Bearer {token}"))
                )
                // 设置API文档信息
                .info(new Info()
                        .title("GoPair API文档")
                        .description("GoPair项目API接口文档")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("GoPair Team")
                                .email("gopair@example.com")
                                .url("https://github.com/gopair"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("http://www.apache.org/licenses/LICENSE-2.0.html"))
                );
    }
} 